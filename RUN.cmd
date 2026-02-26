@echo off
setlocal
pushd "%~dp0"

echo Dang chay mac dinh PUBLIC (alias RUN.cmd)...
call ".\RUN_PUBLIC.cmd" %*
set "RC=%ERRORLEVEL%"

popd
exit /b %RC%
