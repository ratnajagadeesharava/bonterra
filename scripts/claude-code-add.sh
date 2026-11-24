#!/usr/bin/env bash
# Registers the three Community Bridge servers (Priya, Arjun, Neha) with Claude Code, for this project only
# (local scope). Then run `claude` in this directory and type /mcp to check they are connected.
#
#   ./scripts/claude-code-add.sh            # add priya, arjun, neha
#   ./scripts/claude-code-add.sh --remove   # remove them again
. "$(dirname "$0")/lib.sh"
command -v claude >/dev/null 2>&1 || die "claude (Claude Code CLI) not found on PATH."
cd "$ROOT"

names=(priya arjun neha)
ids=(stf_cb_case stf_cb_pm stf_cb_fin)

if [ "${1:-}" = "--remove" ]; then
  for n in "${names[@]}"; do
    claude mcp remove "$n" || true
  done
  exit 0
fi

ensure_jar
[ -f "$DB" ] || "$ROOT/scripts/reset-db.sh"
for i in "${!names[@]}"; do
  claude mcp add "${names[$i]}" \
    --env "CB_STAFF_ID=${ids[$i]}" --env "CB_DB_PATH=$DB" \
    -- "$JAVA" $JAVA_OPTS -jar "$JAR"
done
echo "Added: ${names[*]}. Start Claude Code here and run /mcp to see them."
