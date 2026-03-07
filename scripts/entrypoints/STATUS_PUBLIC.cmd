@echo off
setlocal
pushd "%~dp0"
call "..\manual-status.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
