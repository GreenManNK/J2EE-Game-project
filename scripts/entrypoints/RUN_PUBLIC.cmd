@echo off
setlocal
pushd "%~dp0"
call "..\manual-start-public.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
