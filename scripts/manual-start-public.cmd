@echo off
setlocal
pushd "%~dp0\.."

echo [1/1] Dang chay app + quick tunnel public...
echo Vui long doi script in ra dong PUBLIC_GAME_URL=...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-remote-public-session.ps1"
set "RC=%ERRORLEVEL%"

echo.
if not "%RC%"=="0" (
  echo Co loi khi chay session public.
  echo Kiem tra log:
  echo   run-prod-public.out.log
  echo   run-prod-public.err.log
  echo   cloudflared.err.log
)

popd
if /I not "%~1"=="--no-pause" pause
exit /b %RC%
