#!/usr/bin/env sh
# Reproducible MCP demo (see demo.ps1 for the Windows version). Run from anywhere:  ./scripts/demo.sh
set -eu
cd "$(dirname "$0")/.."

./mvnw -q -B -DskipTests package
./mvnw -q -B test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test

CB_DB_PATH=data/demo.sqlite java --enable-native-access=ALL-UNNAMED -jar target/cb-mcp.jar --reset

SEP=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac
CP="target/test-classes${SEP}target/classes${SEP}$(cat target/test-classpath.txt)"
java --enable-native-access=ALL-UNNAMED -cp "$CP" org.communitybridge.demo.DemoClient target/cb-mcp.jar data/demo.sqlite \
  | tee docs/demo-output.txt
