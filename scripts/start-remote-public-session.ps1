param(
    [switch]$AutoBuild,
    [int]$Port = 8080,
    [int]$WaitSeconds = 30,
    [string]$PublicUrlFile = "public-game-url.txt",
    [string]$AppEnvFile = ".env.public.local",
    [switch]$OpenBrowser,
    [switch]$SkipBootstrap,
    [switch]$ForceBootstrap,
    [ValidateSet("auto", "h2", "mysql", "postgres")]
    [string]$BootstrapDb = "auto"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$appStartScript = Join-Path $PSScriptRoot "start-prod-app.ps1"
$tunnelStartScript = Join-Path $PSScriptRoot "start-remote-quick-tunnel.ps1"
$cfErrLog = Join-Path $repoRoot "cloudflared.err.log"
$appOutLog = Join-Path $repoRoot "run-prod-public.out.log"
$appErrLog = Join-Path $repoRoot "run-prod-public.err.log"
$appPidFile = Join-Path $repoRoot "app-prod.pid"
$tunnelPidFile = Join-Path $repoRoot "cloudflared.pid"
$publicUrlFilePath = if ([System.IO.Path]::IsPathRooted($PublicUrlFile)) { $PublicUrlFile } else { Join-Path $repoRoot $PublicUrlFile }
$bootstrapScript = Join-Path $PSScriptRoot "dev-env-bootstrap.ps1"

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

function Test-ProcessAliveByPidFile([string]$PidFilePath) {
    if (-not (Test-Path $PidFilePath)) {
        return $false
    }
    try {
        $rawPid = Get-Content $PidFilePath -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $rawPid) {
            return $false
        }
        $null = Get-Process -Id ([int]$rawPid) -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
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

function Resolve-BootstrapDbKind([string]$ExplicitDb, [string]$EnvFilePath) {
    $valid = @("auto", "h2", "mysql", "postgres")
    $explicit = [string]$ExplicitDb
    if (-not [string]::IsNullOrWhiteSpace($explicit)) {
        $e = $explicit.Trim().ToLowerInvariant()
        if (($valid -contains $e) -and $e -ne "auto") { return $e }
    }
    $envKind = [string]([Environment]::GetEnvironmentVariable("APP_DATASOURCE_KIND", "Process"))
    if (-not [string]::IsNullOrWhiteSpace($envKind)) {
        $k = $envKind.Trim().ToLowerInvariant()
        if ($valid -contains $k) { return $k }
    }
    $name = [string]([System.IO.Path]::GetFileName($EnvFilePath))
    $n = $name.ToLowerInvariant()
    if ($n.Contains(".mysql.")) { return "mysql" }
    if ($n.Contains(".postgres.")) { return "postgres" }
    return "auto"
}

Require-Script $appStartScript
Require-Script $tunnelStartScript
if (-not $SkipBootstrap) {
    Require-Script $bootstrapScript
}

Push-Location $repoRoot
try {
    if (-not $SkipBootstrap) {
        $bootstrapDbKind = Resolve-BootstrapDbKind -ExplicitDb $BootstrapDb -EnvFilePath $AppEnvFile
        Write-Output "STEP=BOOTSTRAP_ENV"
        if ($ForceBootstrap) {
            & $bootstrapScript -Mode public -Db $bootstrapDbKind -Force | Out-Host
        } else {
            & $bootstrapScript -Mode public -Db $bootstrapDbKind | Out-Host
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Bootstrap moi truong that bai (exit code $LASTEXITCODE)"
        }
    }

    Write-Output "STEP=START_APP"
    if ($AutoBuild) {
        & $appStartScript -AutoBuild -Port $Port -EnvFile $AppEnvFile -EnableH2Fallback | Out-Host
    } else {
        & $appStartScript -Port $Port -EnvFile $AppEnvFile -EnableH2Fallback | Out-Host
    }

    $localGameUrl = "http://127.0.0.1:$Port/Game"
    $localPingUrl = "http://127.0.0.1:$Port/Game/api/connectivity/ping"
    $localWsInfoUrl = "http://127.0.0.1:$Port/Game/ws/info?t=1"
    $localAppOk = Wait-HttpOk -Url $localPingUrl -TimeoutSeconds $WaitSeconds
    $localWsOk = Wait-HttpOk -Url $localWsInfoUrl -TimeoutSeconds 10

    Write-Output "STEP=START_TUNNEL"
    & $tunnelStartScript -LocalPort $Port -ContextPath "/Game" | Out-Host

    $publicBaseUrl = Get-QuickTunnelBaseUrl -LogPath $cfErrLog
    if (-not $publicBaseUrl) {
        throw "Khong lay duoc quick tunnel URL tu $cfErrLog"
    }

    $publicGameUrl = $publicBaseUrl.TrimEnd("/") + "/Game"
    $publicPingUrl = $publicBaseUrl.TrimEnd("/") + "/Game/api/connectivity/ping"
    $publicWsInfoUrl = $publicBaseUrl.TrimEnd("/") + "/Game/ws/info?t=1"

    # Quick Tunnel can print the URL before edge routing is fully ready.
    # Use a light ping endpoint and give the public edge more grace time.
    $publicProbeTimeoutSeconds = [Math]::Max($WaitSeconds, 75)
    $publicGameOk = Wait-HttpOk -Url $publicPingUrl -TimeoutSeconds $publicProbeTimeoutSeconds
    $publicWsOk = Wait-HttpOk -Url $publicWsInfoUrl -TimeoutSeconds ([Math]::Max(20, [Math]::Min(120, $publicProbeTimeoutSeconds + 15)))
    $publicPageOk = Wait-HttpOk -Url $publicGameUrl -TimeoutSeconds 12

    if (-not $localAppOk) {
        throw "App local khong san sang tai $localPingUrl"
    }
    if (-not $localWsOk) {
        throw "WebSocket endpoint local khong san sang tai $localWsInfoUrl"
    }
    if (-not $publicGameOk) {
        $tunnelAlive = Test-ProcessAliveByPidFile -PidFilePath $tunnelPidFile
        $appAlive = Test-ProcessAliveByPidFile -PidFilePath $appPidFile
        $hint = if ($tunnelAlive -and $appAlive) {
            " (tunnel/app van dang chay; co the edge chua on dinh, thu lai sau 10-30s)"
        } else {
            ""
        }
        throw "Quick tunnel da tao nhung URL public khong truy cap duoc: $publicGameUrl (probe: $publicPingUrl)$hint"
    }
    if (-not $publicWsOk) {
        throw "Quick tunnel URL public truy cap duoc web nhung endpoint ws/info chua san sang: $publicWsInfoUrl"
    }

    @(
        "# Auto-generated by scripts/start-remote-public-session.ps1"
        "PUBLIC_GAME_URL=$publicGameUrl"
        "PUBLIC_BASE_URL=$publicBaseUrl"
        "LOCAL_GAME_URL=$localGameUrl"
        "UPDATED_AT_UTC=$(([DateTime]::UtcNow).ToString('yyyy-MM-ddTHH:mm:ssZ'))"
    ) | Set-Content -Path $publicUrlFilePath -Encoding UTF8

    Write-Output ""
    Write-Output "========================================"
    Write-Output " CONG KHAI SAN SANG - GUI LINK NAY"
    Write-Output " $publicGameUrl"
    Write-Output "========================================"
    Write-Output ""
    Write-Output "SESSION_READY=1"
    Write-Output "LOCAL_GAME_URL=$localGameUrl"
    Write-Output "PUBLIC_GAME_URL=$publicGameUrl"
    Write-Output "PUBLIC_PING_URL=$publicPingUrl"
    Write-Output "LOCAL_GAME_OK=$([int]$localAppOk)"
    Write-Output "LOCAL_WS_OK=$([int]$localWsOk)"
    Write-Output "PUBLIC_GAME_OK=$([int]$publicGameOk)"
    Write-Output "PUBLIC_WS_OK=$([int]$publicWsOk)"
    Write-Output "PUBLIC_PAGE_OK=$([int]$publicPageOk)"
    Write-Output "APP_LOG_OUT=$appOutLog"
    Write-Output "APP_LOG_ERR=$appErrLog"
    Write-Output "TUNNEL_LOG_ERR=$cfErrLog"
    Write-Output "PUBLIC_URL_FILE=$publicUrlFilePath"
    Write-Output "APP_ENV_FILE=$AppEnvFile"

    if ($OpenBrowser) {
        Start-Process $publicGameUrl | Out-Null
    }
} finally {
    Pop-Location
}
