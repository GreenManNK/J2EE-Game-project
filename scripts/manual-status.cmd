@echo off
setlocal
pushd "%~dp0\.."

powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\print-runtime-status.ps1" -Title "STATUS"

popd
if /I not "%~1"=="--no-pause" pause
exit /b 0
