$ErrorActionPreference = "Stop"
$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$pidFile = Join-Path $repoRoot "cloudflared-named.pid"

if (-not (Test-Path $pidFile)) {
    Write-Output "Khong tim thay cloudflared-named.pid"
    exit 0
}

$cfPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $cfPid) {
    Write-Output "cloudflared-named.pid rong"
    exit 0
}

try {
    Stop-Process -Id ([int]$cfPid) -Force -ErrorAction SilentlyContinue
    Write-Output "Da dung Cloudflare named tunnel (PID $cfPid)"
} catch {
    Write-Output "Khong dung duoc PID $cfPid (co the da tat)"
}
