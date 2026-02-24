@echo off
setlocal
pushd "%~dp0\.."

echo ===== STATUS =====
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$appPidRaw=(Get-Content 'app-prod.pid' -ErrorAction SilentlyContinue | Select-Object -First 1);" ^
  "$cfPidRaw=(Get-Content 'cloudflared.pid' -ErrorAction SilentlyContinue | Select-Object -First 1);" ^
  "$appProc=$null; if($appPidRaw){ try{$appProc=Get-Process -Id ([int]$appPidRaw) -ErrorAction Stop}catch{} };" ^
  "$cfProc=$null; if($cfPidRaw){ try{$cfProc=Get-Process -Id ([int]$cfPidRaw) -ErrorAction Stop}catch{} };" ^
  "$listen=Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1;" ^
  "$urlMatch=$null; if(Test-Path 'cloudflared.err.log'){ $urlMatch=Select-String -Path 'cloudflared.err.log' -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches -ErrorAction SilentlyContinue | Select-Object -Last 1; };" ^
  "$url=$null; if($cfProc -and $urlMatch -and $urlMatch.Matches.Count -gt 0){$url=$urlMatch.Matches[$urlMatch.Matches.Count-1].Value + '/Game'};" ^
  "$savedPublicUrl=$null; if(Test-Path 'public-game-url.txt'){ $line=Get-Content 'public-game-url.txt' -ErrorAction SilentlyContinue | Where-Object { $_ -like 'PUBLIC_GAME_URL=*' } | Select-Object -First 1; if($line){ $savedPublicUrl=$line.Substring('PUBLIC_GAME_URL='.Length) } };" ^
  "Write-Output ('APP_PID_FILE=' + ($appPidRaw | ForEach-Object {$_}));" ^
  "Write-Output ('APP_PROCESS_ALIVE=' + ($(if($appProc){'1'} else {'0'})));" ^
  "Write-Output ('APP_LISTEN_8080=' + ($(if($listen){'1'} else {'0'})));" ^
  "Write-Output ('APP_LISTEN_PID=' + ($(if($listen){$listen.OwningProcess}else{''})));" ^
  "Write-Output ('QUICK_TUNNEL_PID_FILE=' + ($cfPidRaw | ForEach-Object {$_}));" ^
  "Write-Output ('QUICK_TUNNEL_PROCESS_ALIVE=' + ($(if($cfProc){'1'} else {'0'})));" ^
  "Write-Output ('QUICK_TUNNEL_URL=' + ($(if($url){$url}else{''})));" ^
  "Write-Output ('LAST_PUBLIC_GAME_URL=' + ($(if($savedPublicUrl){$savedPublicUrl}else{''})));"

echo ==================
echo.
echo Neu QUICK_TUNNEL_URL co gia tri, gui link do cho nguoi choi (dang hoat dong).
echo LAST_PUBLIC_GAME_URL la link lan chay gan nhat da luu (co the da het hieu luc neu tunnel da tat).

popd
if /I not "%~1"=="--no-pause" pause
exit /b 0
