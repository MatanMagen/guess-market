@echo off
REM Guess Market web client, exercise 4. Needs Node.js, and the exercise 3 server running:
REM guess-market.war deployed in Tomcat on localhost:8080.
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
    echo Node.js must be installed and on the PATH: check with  node --version
    pause
    exit /b 1
)

call npm install --no-audit --no-fund

REM Opens the page once the server below has had a moment to start.
start "" cmd /c "timeout /t 2 /nobreak >nul & start http://localhost:3000"
node server.js
pause
