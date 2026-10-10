@echo off
REM Guess Market, exercise 3. Starts the JavaFX client from this folder.
REM The server must already be running: guess-market.war deployed in Tomcat on localhost:8080.
cd /d "%~dp0"

set GM_CP=guess-market-client.jar;guess-market-dto.jar;lib\*
set GM_FX=lib\javafx

java --module-path "%GM_FX%" --add-modules javafx.controls ^
     --enable-native-access=javafx.graphics ^
     --sun-misc-unsafe-memory-access=allow ^
     -cp "%GM_CP%" guessmarket.client.GuessMarketClient

if errorlevel 1 (
    echo.
    echo The client did not start.
    echo Java 25 or newer must be on the PATH: check with  java -version
    echo.
    pause
)
