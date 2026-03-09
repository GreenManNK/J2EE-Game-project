@echo off
setlocal
pushd "%~dp0\.."

set "PS_EXTRA_ARGS=-AutoBuild"
set "SHOULD_PAUSE=1"

:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="--named" (
  set "PS_EXTRA_ARGS=%PS_EXTRA_ARGS% -TunnelMode named"
  shift
  goto parse_args
)
if /I "%~1"=="--vpn" (
  set "PS_EXTRA_ARGS=%PS_EXTRA_ARGS% -TunnelMode named"
  shift
  goto parse_args
)
if /I "%~1"=="--no-pause" (
  set "SHOULD_PAUSE=0"
  shift
  goto parse_args
)
shift
goto parse_args

:args_done

echo [1/1] Dang chay app + public tunnel auto (uu tien named tunnel, fallback quick)...
echo Mac dinh se AutoBuild de luon lay giao dien/chuc nang moi nhat.
echo Vui long doi script in ra dong PUBLIC_GAME_URL=...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-remote-public-session.ps1" %PS_EXTRA_ARGS%
set "RC=%ERRORLEVEL%"

echo.
if not "%RC%"=="0" (
  echo Co loi khi chay session public.
  echo Kiem tra log:
  echo   run-prod-public.out.log
  echo   run-prod-public.err.log
  echo   cloudflared.err.log hoac cloudflared-named.err.log
  echo.
  echo ===== STATUS NHANH =====
  call ".\scripts\manual-status.cmd" --no-pause
  echo.
  echo ===== TAIL run-prod-public.out.log =====
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if(Test-Path 'run-prod-public.out.log'){Get-Content -Path 'run-prod-public.out.log' -Tail 40}"
  echo.
  echo ===== TAIL run-prod-public.err.log =====
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if(Test-Path 'run-prod-public.err.log'){Get-Content -Path 'run-prod-public.err.log' -Tail 40}"
  echo.
  echo ===== TAIL cloudflared.err.log =====
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if(Test-Path 'cloudflared.err.log'){Get-Content -Path 'cloudflared.err.log' -Tail 40}"
  echo.
  echo ===== TAIL cloudflared-named.err.log =====
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if(Test-Path 'cloudflared-named.err.log'){Get-Content -Path 'cloudflared-named.err.log' -Tail 40}"
  echo.
  echo ===== TAIL public-fallback-tunnel.out.log =====
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if(Test-Path 'public-fallback-tunnel.out.log'){Get-Content -Path 'public-fallback-tunnel.out.log' -Tail 40}"
  echo.
  echo ===== TAIL public-fallback-tunnel.err.log =====
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if(Test-Path 'public-fallback-tunnel.err.log'){Get-Content -Path 'public-fallback-tunnel.err.log' -Tail 40}"
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
if "%SHOULD_PAUSE%"=="1" pause
exit /b %RC%
