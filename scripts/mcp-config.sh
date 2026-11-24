#!/usr/bin/env bash
# Prints an MCP client config (the "mcpServers" JSON used by Claude Desktop, Cursor, Windsurf, Cline, ...)
# with absolute paths for this machine: three servers on one database, Priya, Arjun and Neha.
#
#   ./scripts/mcp-config.sh                          # print it
#   ./scripts/mcp-config.sh > ~/.config/Claude/claude_desktop_config.json   # Claude Desktop on Linux
#
# For VS Code (.vscode/mcp.json) use:  ./scripts/mcp-config.sh --vscode
# Paths come from this checkout; JAVA is the absolute java path, since GUI clients may not inherit your PATH.
. "$(dirname "$0")/lib.sh"
ensure_jar
[ -f "$DB" ] || "$ROOT/scripts/reset-db.sh" >&2

json_escape() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
java_json="$(json_escape "$JAVA")"
jar_json="$(json_escape "$JAR")"
db_json="$(json_escape "$DB")"

key="mcpServers"
type_line=""
if [ "${1:-}" = "--vscode" ]; then
  key="servers"
  type_line='"type": "stdio", '
fi

server() { # name staffId last
  local comma=","
  [ "$3" = "last" ] && comma=""
  cat <<EOF
    "$1": {
      ${type_line}"command": "$java_json",
      "args": ["$JAVA_OPTS", "-jar", "$jar_json"],
      "env": { "CB_STAFF_ID": "$2", "CB_DB_PATH": "$db_json" }
    }$comma
EOF
}

echo "{"
echo "  \"$key\": {"
server "community-bridge-priya-case-worker" stf_cb_case more
server "community-bridge-arjun-program-manager" stf_cb_pm more
server "community-bridge-neha-finance" stf_cb_fin last
echo "  }"
echo "}"
