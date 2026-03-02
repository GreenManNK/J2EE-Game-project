@echo off
setlocal
pushd "%~dp0\.."

echo [1/1] Dung app Docker...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\dev-stop-docker.ps1"
set "RC=%ERRORLEVEL%"

popd
if /I not "%~1"=="--no-pause" pause
exit /b %RC%
