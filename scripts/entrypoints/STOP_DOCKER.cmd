@echo off
setlocal
pushd "%~dp0"
call "..\manual-stop-docker.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
