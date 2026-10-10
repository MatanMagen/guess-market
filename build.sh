#!/bin/bash
# Builds Guess Market exercise 3: the server war and the JavaFX client folder.
#
#   ./build.sh            compile, jar, assemble dist/
#
# dist/guess-market.war   drop into tomcat/webapps; it carries every jar it needs
# dist/client/            the JavaFX client, started with run.bat
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
CLIENT_JAR="guess-market-client.jar"
WAR="guess-market.war"
CLIENT_MAIN="guessmarket.client.GuessMarketClient"

BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"
WAR_DIR="$BUILD_DIR/war"
CLIENT_DIR="$DIST_DIR/client"

# The class files in these jars are the same on every platform, so they are what the client is
# compiled against; only the native libraries beside them are windows specific.
JAVAFX_DIR="$PROJECT_DIR/lib/javafx-win"
JAVAFX_MODULES="javafx.controls"

# What the engine needs at run time: JAXB left the JDK at Java 11.
JAXB_JARS=(jakarta.xml.bind-api.jar jaxb-core.jar jaxb-impl.jar jakarta.activation-api.jar angus-activation.jar)
GSON_JAR="$PROJECT_DIR/lib/gson/gson-2.11.0.jar"
HTTP_DIR="$PROJECT_DIR/lib/http"
# Tomcat provides the servlet API itself; this copy is only compiled against.
SERVLET_API="$PROJECT_DIR/lib/servlet/servlet-api.jar"

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
mkdir -p "$BUILD_DIR"/{dto,engine,server,client}-classes "$BUILD_DIR/jars" \
         "$WAR_DIR/WEB-INF/classes" "$WAR_DIR/WEB-INF/lib" \
         "$CLIENT_DIR/lib/javafx"

compile() {  # name, class path, extra javac arguments...
  local name="$1" classpath="$2"
  shift 2
  echo "==> Compiling $name"
  find "$PROJECT_DIR/$name/src" -name '*.java' > "$BUILD_DIR/$name-sources.txt"
  "$JAVAC" -encoding UTF-8 -Xlint:all -Werror "$@" \
    -cp "$classpath" -d "$BUILD_DIR/$name-classes" "@$BUILD_DIR/$name-sources.txt"
}

# The dto module is compiled first because it depends on nothing: it is the shared vocabulary
# that the engine, the server and the client all speak.
compile dto "."
"$JAR" --create --file "$BUILD_DIR/jars/$DTO_JAR" -C "$BUILD_DIR/dto-classes" .

compile engine "$BUILD_DIR/jars/$DTO_JAR:$PROJECT_DIR/lib/*"
"$JAR" --create --file "$BUILD_DIR/jars/$ENGINE_JAR" -C "$BUILD_DIR/engine-classes" .

compile server "$BUILD_DIR/jars/$DTO_JAR:$BUILD_DIR/jars/$ENGINE_JAR:$GSON_JAR:$SERVLET_API"

compile client "$BUILD_DIR/jars/$DTO_JAR:$GSON_JAR:$HTTP_DIR/*" \
  --module-path "$JAVAFX_DIR" --add-modules "$JAVAFX_MODULES"

echo "==> Packing $WAR"
# The war carries everything the server needs, since the grader's Tomcat provides nothing but
# the servlet API: the engine, the dto records, Gson and the JAXB runtime.
cp -R "$BUILD_DIR/server-classes/." "$WAR_DIR/WEB-INF/classes/"
cp "$BUILD_DIR/jars/$DTO_JAR" "$BUILD_DIR/jars/$ENGINE_JAR" "$GSON_JAR" "$WAR_DIR/WEB-INF/lib/"
for jar in "${JAXB_JARS[@]}"; do
  cp "$PROJECT_DIR/lib/$jar" "$WAR_DIR/WEB-INF/lib/"
done
cp "$PROJECT_DIR/server/web/"* "$WAR_DIR/"
"$JAR" --create --file "$DIST_DIR/$WAR" -C "$WAR_DIR" .

echo "==> Packing $CLIENT_JAR"
# JavaFX is not named in the manifest: it is a module, so it has to arrive on the module path,
# which is what run.bat passes.
CLASS_PATH_ENTRY="$DTO_JAR lib/$(basename "$GSON_JAR")"
for jar in "$HTTP_DIR"/*.jar; do
  CLASS_PATH_ENTRY="$CLASS_PATH_ENTRY lib/$(basename "$jar")"
done
{
  echo "Main-Class: $CLIENT_MAIN"
  echo "Class-Path: $CLASS_PATH_ENTRY"
  echo "Implementation-Title: Guess Market client (Exercise 3)"
  echo "Implementation-Version: 3.0"
} > "$BUILD_DIR/client-manifest.txt"
"$JAR" --create --file "$CLIENT_DIR/$CLIENT_JAR" \
  --manifest "$BUILD_DIR/client-manifest.txt" -C "$BUILD_DIR/client-classes" .

echo "==> Assembling dist/client"
cp "$BUILD_DIR/jars/$DTO_JAR" "$CLIENT_DIR/"
cp "$GSON_JAR" "$HTTP_DIR"/*.jar "$CLIENT_DIR/lib/"
# Jars and native libraries go into one folder: JavaFX looks for its natives beside its own
# jars before it looks anywhere else, which saves the launcher a library path argument.
cp "$JAVAFX_DIR"/* "$CLIENT_DIR/lib/javafx/"
cp "$PROJECT_DIR/packaging/run.bat" "$PROJECT_DIR/packaging/run.sh" "$CLIENT_DIR/"
chmod +x "$CLIENT_DIR/run.sh"

# The test files ride along so the grader has something to upload straight away.
mkdir -p "$DIST_DIR/test-files"
cp "$PROJECT_DIR"/test-files/ex3/* "$DIST_DIR/test-files/"

echo "==> Build finished"
find "$DIST_DIR" -maxdepth 2 -type f -not -path '*/lib/*' | sed "s|$DIST_DIR|dist|" | sort
echo "dist/client/lib/  ($(find "$CLIENT_DIR/lib" -type f | wc -l | tr -d ' ') files)"
