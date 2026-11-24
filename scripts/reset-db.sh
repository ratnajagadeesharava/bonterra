#!/usr/bin/env bash
# Replaces all data with the synthetic seed (two nonprofits). Separate from normal server startup.
# Uses CB_DB_PATH if set, otherwise data/cb.sqlite in the repository.
#
#   ./scripts/reset-db.sh
. "$(dirname "$0")/lib.sh"
ensure_jar
CB_DB_PATH="$DB" "$JAVA" $JAVA_OPTS -jar "$JAR" --reset
