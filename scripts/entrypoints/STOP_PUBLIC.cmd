@echo off
setlocal
pushd "%~dp0"
call "..\manual-stop-all.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
