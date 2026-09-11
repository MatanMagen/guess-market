@echo off
REM Guess Market, exercise 2. Starts the JavaFX application from this folder.
cd /d "%~dp0"

set GM_CP=guess-market-ui.jar;guess-market-engine.jar;guess-market-dto.jar;lib\*
set GM_FX=lib\javafx

java --module-path "%GM_FX%" --add-modules javafx.controls ^
     --enable-native-access=javafx.graphics ^
     -cp "%GM_CP%" guessmarket.ui.GuessMarketApp

if errorlevel 1 (
    echo.
    echo The application did not start. Java 25 or newer must be on the PATH.
    echo Check with: java -version
    echo.
    pause
)
