#!/usr/bin/env bash
# Reproducible MCP demo, no language model: builds the jar, resets a separate demo database, then runs
# DemoClient, which launches the server once per identity (Priya, Arjun, Neha) and drives
# discover -> create -> approve -> pay -> summary. Output is shown and saved to docs/demo-output.txt.
#
#   ./scripts/demo.sh
. "$(dirname "$0")/lib.sh"
require_java
cd "$ROOT"

./mvnw -q -B -DskipTests package
./mvnw -q -B test-compile dependency:build-classpath \
  -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test

DEMO_DB="$ROOT/data/demo.sqlite"
CB_DB_PATH="$DEMO_DB" "$JAVA" $JAVA_OPTS -jar "$JAR" --reset

SEP=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac
CP="target/test-classes${SEP}target/classes${SEP}$(cat target/test-classpath.txt)"

# DemoClient launches servers with "java" from PATH; make sure that is the same JDK.
PATH="$(dirname "$JAVA"):$PATH" "$JAVA" $JAVA_OPTS -cp "$CP" org.communitybridge.demo.DemoClient "$JAR" "$DEMO_DB" \
  | tee docs/demo-output.txt
