@echo off
setlocal
pushd "%~dp0"
call ".\scripts\manual-verify-public-all.cmd" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
