#!/bin/bash
# Builds and runs the exercise 1 test suite.
#
#   ./test.sh
#
# The tests are not part of the submission; they exist to prove the engine agrees with the
# figures in the exercise and with the supplied test files.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${GM_JAVA_HOME:-$HOME/jdks/jdk-25.0.4+7/Contents/Home}"

BUILD_DIR="$PROJECT_DIR/build"
TEST_CLASSES="$BUILD_DIR/test-classes"
SCRATCH="$BUILD_DIR/test-scratch"

BUILD_LOG="$(mktemp -t guess-market-build)"
trap 'rm -f "$BUILD_LOG"' EXIT
"$PROJECT_DIR/build.sh" > "$BUILD_LOG" 2>&1 || { cat "$BUILD_LOG"; exit 1; }
echo "==> Build ok"

rm -rf "$TEST_CLASSES" "$SCRATCH"
mkdir -p "$TEST_CLASSES" "$SCRATCH"

echo "==> Compiling tests"
find "$PROJECT_DIR/tests/src" -name '*.java' > "$BUILD_DIR/test-sources.txt"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -Xlint:all -Werror \
  -cp "$PROJECT_DIR/dist/guess-market-engine.jar:$PROJECT_DIR/dist/guess-market-dto.jar:$PROJECT_DIR/lib/*" \
  -d "$TEST_CLASSES" \
  "@$BUILD_DIR/test-sources.txt"

echo "==> Running tests"
"$JAVA_HOME/bin/java" \
  -cp "$TEST_CLASSES:$PROJECT_DIR/dist/guess-market-engine.jar:$PROJECT_DIR/dist/guess-market-dto.jar:$PROJECT_DIR/lib/*" \
  guessmarket.tests.Ex1Tests "$PROJECT_DIR/test-files" "$SCRATCH"
