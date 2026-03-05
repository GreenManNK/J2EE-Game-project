@echo off
setlocal
pushd "%~dp0"
call ".\STOP_PUBLIC.cmd" --no-pause %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
