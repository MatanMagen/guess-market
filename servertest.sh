#!/bin/bash
# Deploys the war into a Tomcat, starts it, runs the HTTP suite against it, and stops it again.
#
#   GM_TOMCAT=/path/to/apache-tomcat-10.1.x ./servertest.sh
#
# Expects test.sh to have built everything already. The Tomcat is used as a scratch server: its
# webapps folder gets this war and it is started on port 8080.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${GM_JAVA_HOME:-$HOME/jdks/jdk-25.0.4+7/Contents/Home}"
export JAVA_HOME
TOMCAT="${GM_TOMCAT:?set GM_TOMCAT to an Apache Tomcat 10.1 folder}"
URL="http://localhost:8080/guess-market"
BUILD_DIR="$PROJECT_DIR/build"

TEST_CP="$BUILD_DIR/test-classes:$BUILD_DIR/jars/guess-market-engine.jar:$BUILD_DIR/jars/guess-market-dto.jar"
TEST_CP="$TEST_CP:$BUILD_DIR/client-classes:$PROJECT_DIR/lib/*:$PROJECT_DIR/lib/gson/*:$PROJECT_DIR/lib/http/*"

chmod +x "$TOMCAT"/bin/*.sh
rm -rf "$TOMCAT/webapps/guess-market" "$TOMCAT/webapps/guess-market.war"
cp "$PROJECT_DIR/dist/guess-market.war" "$TOMCAT/webapps/"

echo "==> Starting Tomcat"
"$TOMCAT/bin/startup.sh" > /dev/null
trap '"$TOMCAT/bin/shutdown.sh" > /dev/null 2>&1 || true' EXIT

for _ in $(seq 1 60); do
  curl -sf "$URL/" > /dev/null && break
  sleep 1
done
curl -sf "$URL/" > /dev/null || { echo "the war never came up"; tail -50 "$TOMCAT/logs/catalina.out"; exit 1; }
echo "==> Deployed at $URL"

"$JAVA_HOME/bin/java" -cp "$TEST_CP" guessmarket.tests.ServerTests "$URL" "$PROJECT_DIR/test-files"
