#!/bin/bash
# Builds and runs the engine test suite.
#
#   ./test.sh
#
# The tests are not part of the submission; they exist to prove the engine agrees with the
# figures in the exercise and with the supplied test files. servertest.sh runs the HTTP suite.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${GM_JAVA_HOME:-$HOME/jdks/jdk-25.0.4+7/Contents/Home}"

BUILD_DIR="$PROJECT_DIR/build"
TEST_CLASSES="$BUILD_DIR/test-classes"

BUILD_LOG="$(mktemp -t guess-market-build.XXXXXX)"
trap 'rm -f "$BUILD_LOG"' EXIT
"$PROJECT_DIR/build.sh" > "$BUILD_LOG" 2>&1 || { cat "$BUILD_LOG"; exit 1; }
echo "==> Build ok"

# The server suite drives the server through the client's own HTTP class, so the tests see the
# client classes and their libraries as well as the engine.
TEST_CP="$BUILD_DIR/jars/guess-market-engine.jar:$BUILD_DIR/jars/guess-market-dto.jar:$BUILD_DIR/client-classes"
TEST_CP="$TEST_CP:$PROJECT_DIR/lib/*:$PROJECT_DIR/lib/gson/*:$PROJECT_DIR/lib/http/*"

rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"
echo "==> Compiling tests"
find "$PROJECT_DIR/tests/src" -name '*.java' > "$BUILD_DIR/test-sources.txt"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -Xlint:all -Werror \
  -cp "$TEST_CP" -d "$TEST_CLASSES" "@$BUILD_DIR/test-sources.txt"

echo "==> Running engine tests"
"$JAVA_HOME/bin/java" -cp "$TEST_CLASSES:$TEST_CP" guessmarket.tests.EngineTests "$PROJECT_DIR/test-files"
