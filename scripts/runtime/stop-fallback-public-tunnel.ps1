$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$pidFile = Join-Path $repoRoot "public-fallback-tunnel.pid"

if (-not (Test-Path $pidFile)) {
    Write-Output "Khong tim thay public-fallback-tunnel.pid"
    exit 0
}

$pidValue = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pidValue) {
    Write-Output "public-fallback-tunnel.pid rong"
    exit 0
}

try {
    Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
    Write-Output "Da dung fallback public tunnel (PID $pidValue)"
} catch {
    Write-Output "Khong dung duoc PID $pidValue (co the da tat)"
}
