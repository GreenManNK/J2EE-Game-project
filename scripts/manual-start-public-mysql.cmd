@echo off
setlocal
pushd "%~dp0\.."

set "APP_DATASOURCE_KIND=mysql"
set "APP_DATASOURCE_ALLOW_H2_FALLBACK=false"

echo [1/1] Dang chay app + public tunnel auto (uu tien named tunnel, fallback quick) (MySQL Standard / khong can Laragon)...
echo Script se dung APP_DATASOURCE_MYSQL_* tu file .env.public.mysql.local neu co.
if not exist ".env.public.mysql.local" (
  echo [Canh bao] Chua co .env.public.mysql.local. Se dung bien moi truong hien tai / default.
  echo [Canh bao] Hay copy .env.public.mysql.example ^> .env.public.mysql.local va dien thong tin MySQL.
)
echo Vui long doi script in ra dong PUBLIC_GAME_URL=...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-remote-public-session.ps1" -AutoBuild -AppEnvFile ".env.public.mysql.local"
set "RC=%ERRORLEVEL%"

echo.
if not "%RC%"=="0" (
  echo Co loi khi chay session public (MySQL).
  echo Kiem tra log:
  echo   run-prod-public.out.log
  echo   run-prod-public.err.log
  echo   cloudflared.err.log hoac cloudflared-named.err.log
  echo.
  powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\print-runtime-status.ps1" -Title "STATUS SAU LOI KHOI DONG"
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
  echo.
  powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\print-runtime-status.ps1" -Title "STATUS SAU KHI START"
)

popd
if /I not "%~1"=="--no-pause" pause
exit /b %RC%
