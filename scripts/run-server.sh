#!/usr/bin/env bash
# Starts the stdio MCP server as one staff member. stdin/stdout carry MCP JSON-RPC; logs go to stderr.
# Normally an MCP client launches this for you; this script is for manual runs and for clients that
# want a single command.
#
#   ./scripts/run-server.sh stf_cb_case                # Priya, case worker, Community Bridge
#   CB_DB_PATH=/tmp/x.sqlite ./scripts/run-server.sh stf_cb_pm
#
# Staff IDs: stf_cb_case, stf_cb_pm, stf_cb_fin (Community Bridge); stf_hr_case, stf_hr_pm, stf_hr_fin (Harbor Relief).
# Run ./scripts/reset-db.sh first on a new database, or the identity will not be found (exit code 2).
. "$(dirname "$0")/lib.sh"
check_staff_id "${1:-}"
ensure_jar
export CB_STAFF_ID="$1" CB_DB_PATH="$DB"
exec "$JAVA" $JAVA_OPTS -jar "$JAR"
