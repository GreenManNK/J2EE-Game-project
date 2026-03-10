@echo off
setlocal
pushd "%~dp0\.."

set "ACTION=start"
set "MODE=auto"
set "DB=auto"
set "TUNNEL=auto"
set "STOP_SCOPE=all"
set "SHOULD_PAUSE=1"
set "FORCE_BOOTSTRAP="
set "VERIFY_NO_LIVE="

if /I "%~1"=="start" (
  set "ACTION=start"
  shift
  goto parse_args
)
if /I "%~1"=="status" (
  set "ACTION=status"
  shift
  goto parse_args
)
if /I "%~1"=="stop" (
  set "ACTION=stop"
  shift
  goto parse_args
)
if /I "%~1"=="verify" (
  set "ACTION=verify"
  shift
  goto parse_args
)
if /I "%~1"=="help" goto help

:parse_args
if "%~1"=="" goto dispatch
if /I "%~1"=="--auto" (
  set "MODE=auto"
  set "STOP_SCOPE=all"
  shift
  goto parse_args
)
if /I "%~1"=="--local" (
  set "MODE=local"
  set "STOP_SCOPE=app"
  shift
  goto parse_args
)
if /I "%~1"=="--public" (
  set "MODE=public"
  set "STOP_SCOPE=app"
  shift
  goto parse_args
)
if /I "%~1"=="--docker" (
  set "MODE=docker"
  set "STOP_SCOPE=docker"
  shift
  goto parse_args
)
if /I "%~1"=="--all" (
  set "STOP_SCOPE=all"
  shift
  goto parse_args
)
if /I "%~1"=="--mysql" (
  set "DB=mysql"
  shift
  goto parse_args
)
if /I "%~1"=="--postgres" (
  set "DB=postgres"
  shift
  goto parse_args
)
if /I "%~1"=="--h2" (
  set "DB=h2"
  shift
  goto parse_args
)
if /I "%~1"=="--named" (
  set "TUNNEL=named"
  shift
  goto parse_args
)
if /I "%~1"=="--vpn" (
  set "TUNNEL=named"
  shift
  goto parse_args
)
if /I "%~1"=="--quick" (
  set "TUNNEL=quick"
  shift
  goto parse_args
)
if /I "%~1"=="--no-pause" (
  set "SHOULD_PAUSE=0"
  shift
  goto parse_args
)
if /I "%~1"=="--force-bootstrap" (
  set "FORCE_BOOTSTRAP=1"
  shift
  goto parse_args
)
if /I "%~1"=="--no-live" (
  set "VERIFY_NO_LIVE=1"
  shift
  goto parse_args
)
if /I "%~1"=="--help" goto help
echo [WARN] Bo qua tham so khong ho tro: %~1
shift
goto parse_args

:dispatch
if /I "%ACTION%"=="start" goto run
if /I "%ACTION%"=="status" goto status
if /I "%ACTION%"=="stop" goto stop
if /I "%ACTION%"=="verify" goto verify
goto help

:run
echo [Launcher] Dang chuan doan moi truong, tool va DB de chay du an...
set "PS_ARGS=-Mode %MODE% -Db %DB% -TunnelMode %TUNNEL%"
if defined FORCE_BOOTSTRAP set "PS_ARGS=%PS_ARGS% -ForceBootstrap"

powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\start-project.ps1" %PS_ARGS%
set "RC=%ERRORLEVEL%"

goto finish

:status
echo [Launcher] Dang hien thi trang thai runtime...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\print-runtime-status.ps1" -Title "STATUS"
set "RC=%ERRORLEVEL%"
goto finish

:stop
set "RC=0"
if /I "%STOP_SCOPE%"=="docker" goto stop_docker
if /I "%STOP_SCOPE%"=="app" goto stop_app

echo [Launcher] Dang dung tat ca stack dang chay...
echo [1/5] Dung quick tunnel (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-remote-quick-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [2/5] Dung Cloudflare named tunnel (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-cloudflare-named-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [3/5] Dung fallback public tunnel (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-fallback-public-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [4/5] Dung app production...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-prod-app.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [5/5] Dung Docker mode (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\dev-stop-docker.ps1" -SkipStatus
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo.
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\print-runtime-status.ps1" -Title "STATUS SAU KHI DUNG"
goto finish

:stop_app
echo [Launcher] Dang dung app + tunnel...
echo [1/4] Dung quick tunnel (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-remote-quick-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [2/4] Dung Cloudflare named tunnel (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-cloudflare-named-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [3/4] Dung fallback public tunnel (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-fallback-public-tunnel.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo [4/4] Dung app production...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\stop-prod-app.ps1"
if errorlevel 1 set "RC=%ERRORLEVEL%"

echo.
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\print-runtime-status.ps1" -Title "STATUS SAU KHI DUNG"
goto finish

:stop_docker
echo [Launcher] Dang dung Docker mode...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\dev-stop-docker.ps1"
set "RC=%ERRORLEVEL%"
goto finish

:verify
echo [Launcher] Dang kiem tra tat ca chuc nang PUBLIC...
set "PS_ARGS="
if defined VERIFY_NO_LIVE set "PS_ARGS=-NoLive"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\verify-public-all.ps1" %PS_ARGS%
set "RC=%ERRORLEVEL%"
goto finish

:help
echo Su dung:
echo   cmd /c scripts\manual-start.cmd [start] [--local^|--public^|--docker] [--mysql^|--postgres^|--h2] [--named^|--quick] [--force-bootstrap] [--no-pause]
echo   cmd /c scripts\manual-start.cmd status [--no-pause]
echo   cmd /c scripts\manual-start.cmd stop [--all^|--public^|--local^|--docker] [--no-pause]
echo   cmd /c scripts\manual-start.cmd verify [--no-live] [--no-pause]
echo.
echo Mac dinh:
echo   --auto: uu tien public neu du tool, sau do local, roi docker
echo   --auto DB: uu tien PostgreSQL co cau hinh + reachable, sau do MySQL, cuoi cung H2
echo.
echo Vi du:
echo   cmd /c scripts\manual-start.cmd --no-pause
echo   cmd /c scripts\manual-start.cmd --local --no-pause
echo   cmd /c scripts\manual-start.cmd --public --postgres --named --no-pause
echo   cmd /c scripts\manual-start.cmd --docker --no-pause
echo   cmd /c scripts\manual-start.cmd status --no-pause
echo   cmd /c scripts\manual-start.cmd stop --no-pause
echo   cmd /c scripts\manual-start.cmd verify --no-live --no-pause
set "RC=0"

:finish
popd
if "%SHOULD_PAUSE%"=="1" pause
exit /b %RC%
