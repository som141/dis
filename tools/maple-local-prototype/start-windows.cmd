@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Install Node.js 22 LTS or newer from https://nodejs.org/ and retry.
  pause
  exit /b 1
)
node -e "if(Number(process.versions.node.split('.')[0]) < 22) process.exit(1)"
if errorlevel 1 (
  echo Node.js 22 or newer is required. Please update Node.js and retry.
  pause
  exit /b 1
)
if not exist node_modules (
  call npm ci --ignore-scripts
  if errorlevel 1 goto failed
)
call npm run build
if errorlevel 1 goto failed
node src/server.mjs --open
goto end
:failed
echo Setup failed. Keep this window open and check the error above.
:end
pause
