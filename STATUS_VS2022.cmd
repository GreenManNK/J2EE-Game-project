@echo off
setlocal
pushd "%~dp0"
call ".\STATUS_PUBLIC.cmd" --no-pause %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
