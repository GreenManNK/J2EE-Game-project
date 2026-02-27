@echo off
setlocal
pushd "%~dp0\.."

echo [1/3] Dung quick tunnel (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\stop-remote-quick-tunnel.ps1"

echo [2/3] Dung Cloudflare named tunnel (neu co)...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\stop-cloudflare-named-tunnel.ps1"

echo [3/3] Dung app production...
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\stop-prod-app.ps1"

popd
if /I not "%~1"=="--no-pause" pause
exit /b 0
