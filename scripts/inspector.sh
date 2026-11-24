#!/usr/bin/env bash
# Opens MCP Inspector (browser UI for calling tools by hand) connected to the server as one staff member.
# Needs Node.js (npx); the first run downloads @modelcontextprotocol/inspector from npm.
#
#   ./scripts/inspector.sh stf_cb_case       # then click Connect, Tools -> List Tools
#
# To switch identity in the UI: Disconnect, change CB_STAFF_ID under Environment Variables, Connect.
. "$(dirname "$0")/lib.sh"
check_staff_id "${1:-}"
ensure_jar
command -v npx >/dev/null 2>&1 || die "npx not found. Install Node.js 18+ to use MCP Inspector."
[ -f "$DB" ] || { echo "No database at $DB yet; creating it with the seed." >&2; "$ROOT/scripts/reset-db.sh"; }
exec npx -y @modelcontextprotocol/inspector \
  -e "CB_STAFF_ID=$1" -e "CB_DB_PATH=$DB" \
  "$JAVA" $JAVA_OPTS -jar "$JAR"
