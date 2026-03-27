@echo off
setlocal
pushd "%~dp0\.."

set "ACTION=start"
set "SHOULD_PAUSE=1"
set "FORCE_BOOTSTRAP="
set "RC=0"

if /I "%~1"=="start" (
  set "ACTION=start"
  shift
  goto parse_args
)
if /I "%~1"=="stop" (
  set "ACTION=stop"
  shift
  goto parse_args
)
if /I "%~1"=="help" (
  set "ACTION=help"
  shift
  goto parse_args
)
if /I "%~1"=="--help" (
  set "ACTION=help"
  shift
  goto parse_args
)

:parse_args
if "%~1"=="" goto dispatch
if /I "%~1"=="--no-pause" (
  set "SHOULD_PAUSE=0"
  shift
  goto parse_args
)
if /I "%~1"=="--force-bootstrap" (
  if /I not "%ACTION%"=="start" (
    echo [ERR] --force-bootstrap chi dung voi Start ^(Default Public^).
    goto help_fail
  )
  set "FORCE_BOOTSTRAP=1"
  shift
  goto parse_args
)
echo [ERR] Tham so khong ho tro: %~1
goto help_fail

:dispatch
if /I "%ACTION%"=="start" goto run
if /I "%ACTION%"=="stop" goto stop
if /I "%ACTION%"=="help" goto help
goto help

:run
echo [Launcher] Start ^(Default Public^): dang chuan bi app + public tunnel...
set "PS_ARGS="
if defined FORCE_BOOTSTRAP set "PS_ARGS=-ForceBootstrap"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\start-project.ps1" %PS_ARGS%
set "RC=%ERRORLEVEL%"
goto finish

:stop
echo [Launcher] Stop ^(All^): dang dung app + tunnel + docker neu co...

echo [1/5] Dung quick tunnel ^(neu co^)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-remote-quick-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [2/5] Dung Cloudflare named tunnel ^(neu co^)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-cloudflare-named-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [3/5] Dung fallback public tunnel ^(neu co^)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-fallback-public-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [4/5] Dung app production...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-prod-app.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [5/5] Dung Docker mode ^(neu co^)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\dev-stop-docker.ps1" -SkipStatus
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo.
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\print-runtime-status.ps1" -Title "STATUS SAU KHI DUNG"
goto finish

:help
echo Su dung:
echo   cmd /c scripts\manual-start.cmd [start] [--force-bootstrap] [--no-pause]
echo   cmd /c scripts\manual-start.cmd stop [--no-pause]
echo.
echo Quy uoc:
echo   Start ^(Default Public^): tu dong bootstrap moi truong, chon DB kha dung, mo public tunnel va in link cong cong.
echo   Stop ^(All^): dung app, Cloudflare tunnel, fallback tunnel va Docker neu dang chay.
echo.
echo Vi du:
echo   cmd /c scripts\manual-start.cmd
echo   cmd /c scripts\manual-start.cmd --no-pause
echo   cmd /c scripts\manual-start.cmd stop --no-pause
if not defined RC set "RC=0"
goto finish

:help_fail
set "RC=2"
goto help

:finish
popd
if "%SHOULD_PAUSE%"=="1" pause
exit /b %RC%
