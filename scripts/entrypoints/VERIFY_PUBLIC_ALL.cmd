@echo off
setlocal
pushd "%~dp0"
call "..\manual-verify-public-all.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
