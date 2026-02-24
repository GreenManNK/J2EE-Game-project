@echo off
setlocal
pushd "%~dp0\.."

echo [1/1] Dang chay app production local...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-prod-app.ps1"
set "RC=%ERRORLEVEL%"

echo.
if "%RC%"=="0" (
  echo Local URL (J2EE): http://J2EE/Game
  echo Local URL (localhost): http://127.0.0.1:8080/Game
) else (
  echo Co loi khi chay app. Kiem tra log:
  echo   run-prod-public.out.log
  echo   run-prod-public.err.log
)

popd
if /I not "%~1"=="--no-pause" pause
exit /b %RC%
