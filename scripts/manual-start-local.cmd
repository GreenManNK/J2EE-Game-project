@echo off
setlocal
pushd "%~dp0\.."

echo [1/1] Dang chay app production local...
echo [Bootstrap] Kiem tra moi truong lan dau (neu can)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\dev-env-bootstrap.ps1" -Mode local -Db auto
if not "%ERRORLEVEL%"=="0" (
  set "RC=%ERRORLEVEL%"
  echo Bootstrap moi truong that bai. Dung chay app.
  popd
  if /I not "%~1"=="--no-pause" pause
  exit /b %RC%
)
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-prod-app.ps1" -AutoBuild -EnableH2Fallback
set "RC=%ERRORLEVEL%"

echo.
if "%RC%"=="0" (
  echo Local URL ^(J2EE^): http://J2EE/Game
  echo Local URL ^(localhost^): http://127.0.0.1:8080/Game
  echo.
  powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\print-runtime-status.ps1" -Title "STATUS SAU KHI START"
) else (
  echo Co loi khi chay app. Kiem tra log:
  echo   run-prod-public.out.log
  echo   run-prod-public.err.log
  echo.
  powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\print-runtime-status.ps1" -Title "STATUS SAU LOI KHOI DONG"
)

popd
if /I not "%~1"=="--no-pause" pause
exit /b %RC%
