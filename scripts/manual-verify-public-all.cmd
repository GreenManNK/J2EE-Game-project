@echo off
setlocal
pushd "%~dp0\.."

set "NO_LIVE_ARG="
echo %* | findstr /I /C:"--no-live" >nul && set "NO_LIVE_ARG=-NoLive"

echo Dang kiem tra tat ca chuc nang PUBLIC (alias + nut bam + live start/status/stop)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\verify-public-all.ps1" %NO_LIVE_ARG%
set "RC=%ERRORLEVEL%"

popd
if /I not "%~1"=="--no-pause" pause
exit /b %RC%
