#!/bin/bash
# Builds Guess Market exercise 2 into three jars and assembles the runnable folder.
#
#   ./build.sh            compile, jar, assemble dist/
#
# The build deliberately uses nothing but javac and jar: the grader runs the result from a
# plain command prompt with no IDE and no build tool installed.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# The exercise is fixed to Java 25, so the build pins its own JDK rather than trusting
# whatever JAVA_HOME the shell profile happens to export. Override with GM_JAVA_HOME.
JAVA_HOME="${GM_JAVA_HOME:-$HOME/jdks/jdk-25.0.4+7/Contents/Home}"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
REQUIRED_JAVA_MAJOR=25

DTO_JAR="guess-market-dto.jar"
ENGINE_JAR="guess-market-engine.jar"
UI_JAR="guess-market-ui.jar"
MAIN_CLASS="guessmarket.ui.GuessMarketApp"

BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"

# The class files in these jars are the same on every platform, so they are what the ui is
# compiled against; only the native libraries beside them are windows specific.
JAVAFX_DIR="$PROJECT_DIR/lib/javafx-win"
JAVAFX_MODULES="javafx.controls"

RUNTIME_JARS=(
  jakarta.xml.bind-api.jar
  jaxb-core.jar
  jaxb-impl.jar
  jakarta.activation-api.jar
  angus-activation.jar
)

if [ ! -x "$JAVAC" ]; then
  echo "ERROR: no JDK at $JAVA_HOME (set GM_JAVA_HOME to a Java $REQUIRED_JAVA_MAJOR install)" >&2
  exit 1
fi

JAVA_MAJOR="$("$JAVAC" -version 2>&1 | sed -E 's/^javac ([0-9]+).*/\1/')"
if [ "$JAVA_MAJOR" != "$REQUIRED_JAVA_MAJOR" ]; then
  echo "ERROR: this exercise must be built with Java $REQUIRED_JAVA_MAJOR, found $JAVA_MAJOR at $JAVA_HOME" >&2
  exit 1
fi

if [ ! -f "$JAVAFX_DIR/javafx.controls.jar" ]; then
  echo "ERROR: no JavaFX SDK at $JAVAFX_DIR" >&2
  exit 1
fi

echo "==> Using $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

rm -rf "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$BUILD_DIR/dto-classes" "$BUILD_DIR/engine-classes" "$BUILD_DIR/ui-classes" \
         "$DIST_DIR/lib" "$DIST_DIR/lib/javafx"

# The dto module is compiled first because it depends on nothing: it is the shared
# vocabulary that both the engine and the ui speak.
echo "==> Compiling dto module"
find "$PROJECT_DIR/dto/src" -name '*.java' > "$BUILD_DIR/dto-sources.txt"
"$JAVAC" -encoding UTF-8 -Xlint:all -Werror \
  -d "$BUILD_DIR/dto-classes" \
  "@$BUILD_DIR/dto-sources.txt"

echo "==> Packing $DTO_JAR"
"$JAR" --create --file "$DIST_DIR/$DTO_JAR" -C "$BUILD_DIR/dto-classes" .

echo "==> Compiling engine module"
find "$PROJECT_DIR/engine/src" -name '*.java' > "$BUILD_DIR/engine-sources.txt"
"$JAVAC" -encoding UTF-8 -Xlint:all -Werror \
  -cp "$DIST_DIR/$DTO_JAR:$PROJECT_DIR/lib/*" \
  -d "$BUILD_DIR/engine-classes" \
  "@$BUILD_DIR/engine-sources.txt"

echo "==> Packing $ENGINE_JAR"
"$JAR" --create --file "$DIST_DIR/$ENGINE_JAR" -C "$BUILD_DIR/engine-classes" .

echo "==> Compiling ui module"
find "$PROJECT_DIR/ui/src" -name '*.java' > "$BUILD_DIR/ui-sources.txt"
"$JAVAC" -encoding UTF-8 -Xlint:all -Werror \
  --module-path "$JAVAFX_DIR" --add-modules "$JAVAFX_MODULES" \
  -cp "$DIST_DIR/$ENGINE_JAR:$DIST_DIR/$DTO_JAR:$PROJECT_DIR/lib/*" \
  -d "$BUILD_DIR/ui-classes" \
  "@$BUILD_DIR/ui-sources.txt"

# The manifest names the engine jar and the JAXB runtime. JavaFX is not named here: it is a
# module, so it has to arrive on the module path, which is what run.bat passes.
CLASS_PATH_ENTRY="$ENGINE_JAR $DTO_JAR"
for jar in "${RUNTIME_JARS[@]}"; do
  CLASS_PATH_ENTRY="$CLASS_PATH_ENTRY lib/$jar"
done

{
  echo "Main-Class: $MAIN_CLASS"
  echo "Class-Path: $CLASS_PATH_ENTRY"
  echo "Implementation-Title: Guess Market (Exercise 2)"
  echo "Implementation-Version: 2.0"
} > "$BUILD_DIR/ui-manifest.txt"

echo "==> Packing $UI_JAR"
"$JAR" --create --file "$DIST_DIR/$UI_JAR" \
  --manifest "$BUILD_DIR/ui-manifest.txt" \
  -C "$BUILD_DIR/ui-classes" .

echo "==> Assembling dist/"
for jar in "${RUNTIME_JARS[@]}"; do
  cp "$PROJECT_DIR/lib/$jar" "$DIST_DIR/lib/$jar"
done
# Jars and native libraries go into one folder: JavaFX looks for its natives beside its own
# jars before it looks anywhere else, which saves the launcher a library path argument.
cp "$JAVAFX_DIR"/* "$DIST_DIR/lib/javafx/"

cp "$PROJECT_DIR/packaging/run.bat" "$DIST_DIR/run.bat"
cp "$PROJECT_DIR/packaging/run.sh" "$DIST_DIR/run.sh"
chmod +x "$DIST_DIR/run.sh"

# The test files ride along so the grader has something to open straight away. They are left
# out of the submission zip, which carries only what the application needs to run.
mkdir -p "$DIST_DIR/test-files"
cp "$PROJECT_DIR"/test-files/ex2/*.xml "$DIST_DIR/test-files/"
cp "$PROJECT_DIR"/test-files/ex2/*.xsd "$DIST_DIR/test-files/"

# The graded readme is not in the repository, so this is a no-op for a fresh clone.
if [ -f "$PROJECT_DIR/packaging/readme.docx" ]; then
  cp "$PROJECT_DIR/packaging/readme.docx" "$DIST_DIR/readme.docx"
fi

echo "==> Build finished"
find "$DIST_DIR" -maxdepth 2 -type f -not -path '*/lib/javafx/*' | sed "s|$DIST_DIR|dist|" | sort
echo "dist/lib/javafx/  ($(ls "$DIST_DIR/lib/javafx" | wc -l | tr -d ' ') files)"
