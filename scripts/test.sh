#!/usr/bin/env bash
# Builds and runs the tests, then prints a summary and refreshes docs/TEST-CASES.md.
#
#   ./scripts/test.sh          everything: 307 business cases + 71 MCP cases (clean verify, about 1-2 minutes)
#   ./scripts/test.sh unit     business tests only (service level, no server processes)
#   ./scripts/test.sh mcp      MCP tests only (packages the jar, then runs it as real stdio servers)
#
# Exit code is non-zero if any test fails.
. "$(dirname "$0")/lib.sh"
require_java
cd "$ROOT"

mode="${1:-all}"
status=0
case "$mode" in
  all)  ./mvnw -B clean verify || status=$? ;;
  unit) ./mvnw -B clean test || status=$? ;;
  mcp)  ./mvnw -B clean verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false || status=$? ;;
  *)    die "unknown mode '$mode' (use: all, unit, mcp)" ;;
esac

# Summary from the JUnit XML reports.
attr() { # <testsuite ...> line, attribute name -> number (0 if absent)
  local v
  v="$(printf '%s' "$1" | sed -n "s/.* $2=\"\([0-9]*\)\".*/\1/p")"
  echo "${v:-0}"
}
summarize() {
  local dir="$1" label="$2"
  [ -d "$dir" ] || return 0
  local tests=0 failures=0 errors=0 skipped=0 f line
  for f in "$dir"/TEST-*.xml; do
    [ -e "$f" ] || continue
    line="$(grep -m1 -o '<testsuite [^>]*' "$f" || true)"
    tests=$((tests + $(attr "$line" tests)))
    failures=$((failures + $(attr "$line" failures)))
    errors=$((errors + $(attr "$line" errors)))
    skipped=$((skipped + $(attr "$line" skipped)))
  done
  printf '%-15s %4d cases, %4d passed, %d failed, %d errors, %d skipped\n' \
    "$label" "$tests" "$((tests - failures - errors - skipped))" "$failures" "$errors" "$skipped"
}
echo
echo "=== Summary ==="
summarize target/surefire-reports "Business tests"
summarize target/failsafe-reports "MCP tests"

if command -v python3 >/dev/null 2>&1; then
  python3 scripts/test-catalog.py src/test/java target/surefire-reports target/failsafe-reports > docs/TEST-CASES.md
  echo "Per-test results: docs/TEST-CASES.md"
fi
exit "$status"
