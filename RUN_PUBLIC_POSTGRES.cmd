@echo off
setlocal
pushd "%~dp0"
call ".\scripts\manual-start-public-postgres.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
