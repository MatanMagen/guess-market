#!/bin/bash
# Guess Market, exercise 2. Starts the application on macOS or Linux.
#
# The folder next to these jars holds the Windows JavaFX build, which is what the submission
# needs, so a local run points at a JavaFX SDK for this machine instead. Set GM_JAVAFX to it.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${GM_JAVA_HOME:-$HOME/jdks/jdk-25.0.4+7/Contents/Home}"
JAVAFX="${GM_JAVAFX:-$HERE/../course-materials/javafx-mac/lib}"

if [ ! -f "$JAVAFX/javafx.controls.jar" ]; then
  echo "ERROR: no JavaFX SDK for this machine at $JAVAFX (set GM_JAVAFX)" >&2
  exit 1
fi

exec "$JAVA_HOME/bin/java" \
  --module-path "$JAVAFX" --add-modules javafx.controls \
  --enable-native-access=javafx.graphics \
  -cp "$HERE/guess-market-ui.jar:$HERE/guess-market-engine.jar:$HERE/guess-market-dto.jar:$HERE/lib/*" \
  guessmarket.ui.GuessMarketApp "$@"
