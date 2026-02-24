@echo off
setlocal
pushd "%~dp0\.."

echo [1/1] Dang chay app + quick tunnel public...
echo Mac dinh se AutoBuild de luon lay giao dien/chuc nang moi nhat.
echo Vui long doi script in ra dong PUBLIC_GAME_URL=...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-remote-public-session.ps1" -AutoBuild
set "RC=%ERRORLEVEL%"

echo.
if not "%RC%"=="0" (
  echo Co loi khi chay session public.
  echo Kiem tra log:
  echo   run-prod-public.out.log
  echo   run-prod-public.err.log
  echo   cloudflared.err.log
  if exist "public-game-url.txt" del /q "public-game-url.txt" >nul 2>nul
) else (
  if exist "public-game-url.txt" (
    for /f "usebackq tokens=1,* delims==" %%A in ("public-game-url.txt") do (
      if /I "%%A"=="PUBLIC_GAME_URL" set "PUBLIC_GAME_URL=%%B"
    )
  )
  if defined PUBLIC_GAME_URL (
    echo ========================================
    echo LINK CONG CONG DE GUI MOI NGUOI:
    echo %PUBLIC_GAME_URL%
    echo ========================================
  ) else (
    echo Khong doc duoc public-game-url.txt. Hay xem dong PUBLIC_GAME_URL= trong log ben tren.
  )
)

popd
if /I not "%~1"=="--no-pause" pause
exit /b %RC%
