#!/bin/bash
# Guess Market - Exercise 1 - console application.
# The same launch as run.bat, for developing on macOS or Linux.
#
# Picks the Java to run with in this order: GM_JAVA_HOME, then JAVA_HOME, then whatever
# "java" is on the PATH. The exercise requires Java 25.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ -n "${GM_JAVA_HOME:-}" ]; then
  JAVA_BIN="$GM_JAVA_HOME/bin/java"
elif [ -n "${JAVA_HOME:-}" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="java"
fi

exec "$JAVA_BIN" -cp "guess-market-ui.jar:guess-market-engine.jar:guess-market-dto.jar:lib/*" guessmarket.ui.ConsoleApp
