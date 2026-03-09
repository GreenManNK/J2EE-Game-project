@echo off
setlocal
pushd "%~dp0\.."

echo ===== STATUS =====
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$appPidRaw=(Get-Content 'app-prod.pid' -ErrorAction SilentlyContinue | Select-Object -First 1);" ^
  "$quickPidRaw=(Get-Content 'cloudflared.pid' -ErrorAction SilentlyContinue | Select-Object -First 1);" ^
  "$namedPidRaw=(Get-Content 'cloudflared-named.pid' -ErrorAction SilentlyContinue | Select-Object -First 1);" ^
  "$fallbackPidRaw=(Get-Content 'public-fallback-tunnel.pid' -ErrorAction SilentlyContinue | Select-Object -First 1);" ^
  "$appProc=$null; if($appPidRaw){ try{$appProc=Get-Process -Id ([int]$appPidRaw) -ErrorAction Stop}catch{} };" ^
  "$quickProc=$null; if($quickPidRaw){ try{$quickProc=Get-Process -Id ([int]$quickPidRaw) -ErrorAction Stop}catch{} };" ^
  "$namedProc=$null; if($namedPidRaw){ try{$namedProc=Get-Process -Id ([int]$namedPidRaw) -ErrorAction Stop}catch{} };" ^
  "$fallbackProc=$null; if($fallbackPidRaw){ try{$fallbackProc=Get-Process -Id ([int]$fallbackPidRaw) -ErrorAction Stop}catch{} };" ^
  "$listen=Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1;" ^
  "$quickUrlMatch=$null; if(Test-Path 'cloudflared.err.log'){ $quickUrlMatch=Select-String -Path 'cloudflared.err.log' -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches -ErrorAction SilentlyContinue | Select-Object -Last 1; };" ^
  "$quickUrl=$null; if($quickProc -and $quickUrlMatch -and $quickUrlMatch.Matches.Count -gt 0){$quickUrl=$quickUrlMatch.Matches[$quickUrlMatch.Matches.Count-1].Value.TrimEnd('/') + '/Game'};" ^
  "$savedPublicUrl=$null; $savedPublicBase=$null; $savedTunnelMode=$null;" ^
  "if(Test-Path 'public-game-url.txt'){" ^
  "  foreach($line in Get-Content 'public-game-url.txt' -ErrorAction SilentlyContinue){" ^
  "    if($line -like 'PUBLIC_GAME_URL=*'){ $savedPublicUrl=$line.Substring('PUBLIC_GAME_URL='.Length) }" ^
  "    if($line -like 'PUBLIC_BASE_URL=*'){ $savedPublicBase=$line.Substring('PUBLIC_BASE_URL='.Length) }" ^
  "    if($line -like 'TUNNEL_MODE=*'){ $savedTunnelMode=$line.Substring('TUNNEL_MODE='.Length).ToLowerInvariant() }" ^
  "  }" ^
  "};" ^
  "$namedUrl=$null;" ^
  "if($namedProc){" ^
  "  if(-not [string]::IsNullOrWhiteSpace($savedPublicUrl)){ $namedUrl=$savedPublicUrl.TrimEnd('/') }" ^
  "  elseif(-not [string]::IsNullOrWhiteSpace($savedPublicBase)){ $namedUrl=$savedPublicBase.TrimEnd('/') + '/Game' }" ^
  "};" ^
  "$fallbackUrl=$null;" ^
  "if($fallbackProc -and -not [string]::IsNullOrWhiteSpace($savedPublicUrl)){ $fallbackUrl=$savedPublicUrl.TrimEnd('/') };" ^
  "$activeMode='';" ^
  "if($savedTunnelMode -eq 'named' -and $namedProc){ $activeMode='named' }" ^
  "elseif($savedTunnelMode -eq 'quick' -and $quickProc){ $activeMode='quick' }" ^
  "elseif(($savedTunnelMode -eq 'runlocal' -or $savedTunnelMode -eq 'localtunnel') -and $fallbackProc){ $activeMode=$savedTunnelMode }" ^
  "elseif($namedProc){ $activeMode='named' }" ^
  "elseif($quickProc){ $activeMode='quick' }" ^
  "elseif($fallbackProc -and $savedTunnelMode){ $activeMode=$savedTunnelMode };" ^
  "$activePublicUrl='';" ^
  "if($activeMode -eq 'named'){ $activePublicUrl=$namedUrl }" ^
  "elseif($activeMode -eq 'quick'){ $activePublicUrl=$quickUrl }" ^
  "elseif($activeMode -eq 'runlocal' -or $activeMode -eq 'localtunnel'){ $activePublicUrl=$fallbackUrl }" ^
  "Write-Output ('APP_PID_FILE=' + ($appPidRaw | ForEach-Object {$_}));" ^
  "Write-Output ('APP_PROCESS_ALIVE=' + ($(if($appProc){'1'} else {'0'})));" ^
  "Write-Output ('APP_LISTEN_8080=' + ($(if($listen){'1'} else {'0'})));" ^
  "Write-Output ('APP_LISTEN_PID=' + ($(if($listen){$listen.OwningProcess}else{''})));" ^
  "Write-Output ('QUICK_TUNNEL_PID_FILE=' + ($quickPidRaw | ForEach-Object {$_}));" ^
  "Write-Output ('QUICK_TUNNEL_PROCESS_ALIVE=' + ($(if($quickProc){'1'} else {'0'})));" ^
  "Write-Output ('QUICK_TUNNEL_URL=' + ($(if($quickUrl){$quickUrl}else{''})));" ^
  "Write-Output ('NAMED_TUNNEL_PID_FILE=' + ($namedPidRaw | ForEach-Object {$_}));" ^
  "Write-Output ('NAMED_TUNNEL_PROCESS_ALIVE=' + ($(if($namedProc){'1'} else {'0'})));" ^
  "Write-Output ('NAMED_TUNNEL_URL=' + ($(if($namedUrl){$namedUrl}else{''})));" ^
  "Write-Output ('FALLBACK_TUNNEL_PID_FILE=' + ($fallbackPidRaw | ForEach-Object {$_}));" ^
  "Write-Output ('FALLBACK_TUNNEL_PROCESS_ALIVE=' + ($(if($fallbackProc){'1'} else {'0'})));" ^
  "Write-Output ('FALLBACK_TUNNEL_URL=' + ($(if($fallbackUrl){$fallbackUrl}else{''})));" ^
  "Write-Output ('ACTIVE_TUNNEL_MODE=' + $activeMode);" ^
  "Write-Output ('ACTIVE_PUBLIC_GAME_URL=' + ($(if($activePublicUrl){$activePublicUrl}else{''})));" ^
  "Write-Output ('STALE_PUBLIC_GAME_URL=' + ($(if((-not $activeMode) -and $savedPublicUrl){$savedPublicUrl}else{''})));" ^
  "Write-Output ('LAST_TUNNEL_MODE=' + ($(if($savedTunnelMode){$savedTunnelMode}else{''})));" ^
  "Write-Output ('LAST_PUBLIC_BASE_URL=' + ($(if($savedPublicBase){$savedPublicBase}else{''})));" ^
  "Write-Output ('LAST_PUBLIC_GAME_URL=' + ($(if($savedPublicUrl){$savedPublicUrl}else{''})));"

echo ==================
echo.
echo Neu ACTIVE_PUBLIC_GAME_URL co gia tri, gui link do cho nguoi choi.
echo ACTIVE_TUNNEL_MODE cho biet dang dung named, quick, runlocal hoac localtunnel.
echo STALE_PUBLIC_GAME_URL la link cu duoc luu khi hien tai khong co tunnel active.
echo LAST_PUBLIC_GAME_URL la link lan chay gan nhat da luu (co the da het hieu luc neu tunnel da tat).

popd
if /I not "%~1"=="--no-pause" pause
exit /b 0
