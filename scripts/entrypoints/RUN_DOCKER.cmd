@echo off
setlocal
pushd "%~dp0"
call "..\manual-start-docker.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
