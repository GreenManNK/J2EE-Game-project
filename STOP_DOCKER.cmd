@echo off
setlocal
pushd "%~dp0"
call ".\scripts\manual-stop-docker.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
