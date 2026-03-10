@echo off
setlocal
pushd "%~dp0\.."

echo [1/1] Dang chay app bang Docker...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\dev-run-docker.ps1"
set "RC=%ERRORLEVEL%"

if "%RC%"=="0" (
  echo Docker URL: http://127.0.0.1:8080/Game
  echo.
  powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\print-runtime-status.ps1" -Title "STATUS SAU KHI START DOCKER"
) else (
  echo Co loi khi chay Docker. Kiem tra:
  echo   docker compose logs --tail=200
  echo.
  powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\print-runtime-status.ps1" -Title "STATUS SAU LOI KHOI DONG DOCKER"
)

popd
if /I not "%~1"=="--no-pause" pause
exit /b %RC%
