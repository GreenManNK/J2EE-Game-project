param(
    [switch]$AutoBuild,
    [int]$Port = 8080,
    [int]$WaitSeconds = 30
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$appStartScript = Join-Path $PSScriptRoot "start-prod-app.ps1"
$tunnelStartScript = Join-Path $PSScriptRoot "start-remote-quick-tunnel.ps1"
$cfErrLog = Join-Path $repoRoot "cloudflared.err.log"
$appOutLog = Join-Path $repoRoot "run-prod-public.out.log"
$appErrLog = Join-Path $repoRoot "run-prod-public.err.log"

function Require-Script([string]$Path) {
    if (-not (Test-Path $Path)) {
        throw "Khong tim thay script: $Path"
    }
}

function Wait-HttpOk([string]$Url, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($res.StatusCode -eq 200) {
                return $true
            }
        } catch {
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

function Get-QuickTunnelBaseUrl([string]$LogPath) {
    if (-not (Test-Path $LogPath)) {
        return $null
    }
    $matches = Select-String -Path $LogPath -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches -ErrorAction SilentlyContinue
    if (-not $matches) {
        return $null
    }
    $last = $matches | Select-Object -Last 1
    if (-not $last -or $last.Matches.Count -eq 0) {
        return $null
    }
    return $last.Matches[$last.Matches.Count - 1].Value
}

Require-Script $appStartScript
Require-Script $tunnelStartScript

Push-Location $repoRoot
try {
    Write-Output "STEP=START_APP"
    if ($AutoBuild) {
        & $appStartScript -AutoBuild -Port $Port | Out-Host
    } else {
        & $appStartScript -Port $Port | Out-Host
    }

    $localGameUrl = "http://127.0.0.1:$Port/Game"
    $localWsInfoUrl = "http://127.0.0.1:$Port/Game/ws/info?t=1"
    $localAppOk = Wait-HttpOk -Url $localGameUrl -TimeoutSeconds $WaitSeconds
    $localWsOk = Wait-HttpOk -Url $localWsInfoUrl -TimeoutSeconds 10

    Write-Output "STEP=START_TUNNEL"
    & $tunnelStartScript -LocalPort $Port -ContextPath "/Game" | Out-Host

    $publicBaseUrl = Get-QuickTunnelBaseUrl -LogPath $cfErrLog
    if (-not $publicBaseUrl) {
        throw "Khong lay duoc quick tunnel URL tu $cfErrLog"
    }

    $publicGameUrl = $publicBaseUrl.TrimEnd("/") + "/Game"
    $publicWsInfoUrl = $publicBaseUrl.TrimEnd("/") + "/Game/ws/info?t=1"

    $publicGameOk = Wait-HttpOk -Url $publicGameUrl -TimeoutSeconds $WaitSeconds
    $publicWsOk = Wait-HttpOk -Url $publicWsInfoUrl -TimeoutSeconds 15

    Write-Output "SESSION_READY=1"
    Write-Output "LOCAL_GAME_URL=$localGameUrl"
    Write-Output "PUBLIC_GAME_URL=$publicGameUrl"
    Write-Output "LOCAL_GAME_OK=$([int]$localAppOk)"
    Write-Output "LOCAL_WS_OK=$([int]$localWsOk)"
    Write-Output "PUBLIC_GAME_OK=$([int]$publicGameOk)"
    Write-Output "PUBLIC_WS_OK=$([int]$publicWsOk)"
    Write-Output "APP_LOG_OUT=$appOutLog"
    Write-Output "APP_LOG_ERR=$appErrLog"
    Write-Output "TUNNEL_LOG_ERR=$cfErrLog"
} finally {
    Pop-Location
}
