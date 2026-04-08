$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$pidFile = Join-Path $repoRoot "cloudflared.pid"

if (-not (Test-Path $pidFile)) {
    Write-Output "Khong tim thay cloudflared.pid"
    exit 0
}

$cfPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $cfPid) {
    Write-Output "cloudflared.pid rong"
    exit 0
}

try {
    Stop-Process -Id ([int]$cfPid) -Force -ErrorAction SilentlyContinue
    Write-Output "Da dung Quick Tunnel (PID $cfPid)"
} catch {
    Write-Output "Khong dung duoc PID $cfPid (co the da tat)"
}
