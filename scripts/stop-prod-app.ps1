$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $repoRoot "app-prod.pid"

if (-not (Test-Path $pidFile)) {
    Write-Output "Khong tim thay app-prod.pid"
    exit 0
}

$appPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $appPid) {
    Write-Output "app-prod.pid rong"
    exit 0
}

try {
    Stop-Process -Id ([int]$appPid) -Force -ErrorAction SilentlyContinue
    Write-Output "Da dung app production (PID $appPid)"
} catch {
    Write-Output "Khong dung duoc PID $appPid (co the da tat)"
}
