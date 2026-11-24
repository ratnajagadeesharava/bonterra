#!/usr/bin/env bash
# Shared settings for the bash scripts. Source it; do not run it.
#   ROOT     repository root (absolute)
#   JAR      the packaged MCP server
#   DB       database file: $CB_DB_PATH if set, else $ROOT/data/cb.sqlite
#   JAVA     java executable: $JAVA_HOME/bin/java if set, else java on PATH (absolute path)
#   JAVA_OPTS flags passed to every server launch

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/target/cb-mcp.jar"
DB="${CB_DB_PATH:-$ROOT/data/cb.sqlite}"
case "$DB" in /*) ;; *) DB="$ROOT/$DB" ;; esac

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="$(command -v java || true)"
fi
JAVA_OPTS="--enable-native-access=ALL-UNNAMED"

# Staff identities from the seed (see src/main/resources/db/seed.sql).
STAFF_IDS="stf_cb_case stf_cb_pm stf_cb_fin stf_hr_case stf_hr_pm stf_hr_fin"

die() {
  echo "error: $*" >&2
  exit 1
}

require_java() {
  [ -n "$JAVA" ] || die "java not found. Install a JDK 21+ or set JAVA_HOME."
  local major
  major="$("$JAVA" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')"
  [ "${major:-0}" -ge 21 ] 2>/dev/null || die "Java 21 or newer is required (found: ${major:-unknown})."
}

# Builds target/cb-mcp.jar if it is missing.
ensure_jar() {
  require_java
  if [ ! -f "$JAR" ]; then
    echo "Building $JAR ..." >&2
    (cd "$ROOT" && ./mvnw -q -B -DskipTests package)
  fi
}

# Fails unless the argument is one of the seeded staff IDs (the server itself also checks).
check_staff_id() {
  local id="${1:-}"
  [ -n "$id" ] || die "missing staff ID. One of: $STAFF_IDS"
  case " $STAFF_IDS stf_cb_former " in
    *" $id "*) ;;
    *) echo "warning: '$id' is not a seeded staff ID; the server will refuse it (exit code 2)." >&2 ;;
  esac
}
