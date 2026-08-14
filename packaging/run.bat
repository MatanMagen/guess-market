@echo off
REM ---------------------------------------------------------------------------
REM  Guess Market - Exercise 1 - console application
REM
REM  Double click this file, or run it from a command prompt, to start the
REM  application. It must stay in the same folder as the two jars and the lib
REM  folder that came with it.
REM
REM  Requires Java 25 on the PATH.
REM ---------------------------------------------------------------------------
setlocal

cd /d "%~dp0"

java -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo  Java could not be found on this machine.
    echo  Please install Java 25 and make sure "java" runs from a command prompt.
    echo.
    pause
    exit /b 1
)

java -cp "guess-market-ui.jar;guess-market-engine.jar;guess-market-dto.jar;lib\*" guessmarket.ui.ConsoleApp

echo.
pause
endlocal
