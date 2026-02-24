@echo off
setlocal
pushd "%~dp0"

echo Dang chay mac dinh PUBLIC (app + quick tunnel)...
call "%~dp0manual-start-public.cmd" %*
set "RC=%ERRORLEVEL%"

popd
exit /b %RC%
