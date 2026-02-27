@echo off
setlocal
pushd "%~dp0"

echo Dang chay mac dinh PUBLIC (app + tunnel auto: uu tien named tunnel)...
call "%~dp0manual-start-public.cmd" %*
set "RC=%ERRORLEVEL%"

popd
exit /b %RC%
