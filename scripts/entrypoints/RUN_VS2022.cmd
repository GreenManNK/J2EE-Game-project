@echo off
setlocal
pushd "%~dp0"
call ".\RUN.cmd" --no-pause %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
