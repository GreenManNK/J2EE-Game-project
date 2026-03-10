param(
    [int]$LocalPort = 8080,
    [string]$ContextPath = "/Game",
    [switch]$NoDownload
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $repoRoot ".tools"
$cloudflaredExe = Join-Path $toolsDir "cloudflared.exe"
$pidFile = Join-Path $repoRoot "cloudflared.pid"
$outLog = Join-Path $repoRoot "cloudflared.out.log"
$errLog = Join-Path $repoRoot "cloudflared.err.log"

function Normalize-ContextPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        $v = "/Game"
    } else {
        $v = $Path.Trim()
    }
    if (-not $v.StartsWith("/")) { $v = "/" + $v }
    while ($v.Length -gt 1 -and $v.EndsWith("/")) { $v = $v.Substring(0, $v.Length - 1) }
    return $v
}

function Ensure-Cloudflared {
    if (Test-Path $cloudflaredExe) {
        return
    }
    if ($NoDownload) {
        throw "Khong tim thay cloudflared tai: $cloudflaredExe"
    }
    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    $url = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
    Write-Output "Dang tai cloudflared..."
    Invoke-WebRequest -Uri $url -OutFile $cloudflaredExe
}

function Stop-PreviousTunnel {
    if (-not (Test-Path $pidFile)) {
        return
    }
    $existingPid = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if (-not $existingPid) {
        return
    }
    try {
        Stop-Process -Id ([int]$existingPid) -Force -ErrorAction SilentlyContinue
    } catch {
    }
    Start-Sleep -Milliseconds 800
}

function Start-Tunnel([int]$Port) {
    if (Test-Path $outLog) { Remove-Item $outLog -Force -ErrorAction SilentlyContinue }
    if (Test-Path $errLog) { Remove-Item $errLog -Force -ErrorAction SilentlyContinue }

    $args = @("tunnel", "--url", "http://127.0.0.1:$Port", "--no-autoupdate")
    $p = Start-Process -FilePath $cloudflaredExe -ArgumentList $args -PassThru `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog
    $p.Id | Set-Content $pidFile
    return $p
}

function Wait-QuickTunnelUrl {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $errLog) {
            $match = Select-String -Path $errLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches -ErrorAction SilentlyContinue |
                Select-Object -Last 1
            if ($match -and $match.Matches.Count -gt 0) {
                return $match.Matches[$match.Matches.Count - 1].Value
            }
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Khong lay duoc Quick Tunnel URL. Xem log: $errLog"
}

$ContextPath = Normalize-ContextPath $ContextPath
Ensure-Cloudflared
Stop-PreviousTunnel
$process = Start-Tunnel -Port $LocalPort
$baseUrl = Wait-QuickTunnelUrl
$publicAppUrl = $baseUrl + $ContextPath

Write-Output "OK - Remote quick tunnel da san sang."
Write-Output "Public URL: $publicAppUrl"
Write-Output "PID: $($process.Id)"
Write-Output "Local target: http://127.0.0.1:$LocalPort$ContextPath"
Write-Output "Logs: $errLog"
Write-Output "TUNNEL_MODE=quick"
Write-Output "TUNNEL_PID=$($process.Id)"
Write-Output "TUNNEL_READY=1"
Write-Output "PUBLIC_BASE_URL=$baseUrl"
Write-Output "PUBLIC_GAME_URL=$publicAppUrl"
Write-Output "LOCAL_TARGET_URL=http://127.0.0.1:$LocalPort$ContextPath"
Write-Output "LOG_OUT=$outLog"
Write-Output "LOG_ERR=$errLog"
