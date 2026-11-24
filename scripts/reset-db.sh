#!/usr/bin/env sh
# Replaces all data in the database with the synthetic seed. Uses CB_DB_PATH if set, otherwise data/cb.sqlite.
set -eu
cd "$(dirname "$0")/.."
[ -f target/cb-mcp.jar ] || ./mvnw -q -B -DskipTests package
exec java --enable-native-access=ALL-UNNAMED -jar target/cb-mcp.jar --reset
