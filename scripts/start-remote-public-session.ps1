param(
    [switch]$AutoBuild,
    [int]$Port = 8080,
    [int]$WaitSeconds = 30,
    [string]$PublicUrlFile = "public-game-url.txt",
    [string]$AppEnvFile = ".env.public.local",
    [ValidateSet("auto", "named", "quick")]
    [string]$TunnelMode = "auto",
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
$namedTunnelStartScript = Join-Path $PSScriptRoot "start-cloudflare-named-tunnel.ps1"
$stopQuickTunnelScript = Join-Path $PSScriptRoot "stop-remote-quick-tunnel.ps1"
$stopNamedTunnelScript = Join-Path $PSScriptRoot "stop-cloudflare-named-tunnel.ps1"
$cfErrLog = Join-Path $repoRoot "cloudflared.err.log"
$namedCfErrLog = Join-Path $repoRoot "cloudflared-named.err.log"
$appOutLog = Join-Path $repoRoot "run-prod-public.out.log"
$appErrLog = Join-Path $repoRoot "run-prod-public.err.log"
$appPidFile = Join-Path $repoRoot "app-prod.pid"
$tunnelPidFile = Join-Path $repoRoot "cloudflared.pid"
$namedTunnelPidFile = Join-Path $repoRoot "cloudflared-named.pid"
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
            $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 4
            if ($res.StatusCode -ge 200 -and $res.StatusCode -lt 400) {
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

function Load-EnvFile([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = [string]$_
        if ([string]::IsNullOrWhiteSpace($line)) { return }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) { return }
        $idx = $trimmed.IndexOf("=")
        if ($idx -lt 1) { return }
        $name = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1)
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Parse-KeyValueLines([object[]]$Lines) {
    $map = @{}
    foreach ($line in $Lines) {
        $text = [string]$line
        if ($text -match '^([A-Z0-9_]+)=(.*)$') {
            $map[$matches[1]] = $matches[2]
        }
    }
    return $map
}

function Has-NamedTunnelConfig {
    $token = [string]([Environment]::GetEnvironmentVariable("CLOUDFLARE_TUNNEL_TOKEN", "Process"))
    $publicBaseUrl = [string]([Environment]::GetEnvironmentVariable("PUBLIC_BASE_URL", "Process"))
    $publicGameUrl = [string]([Environment]::GetEnvironmentVariable("PUBLIC_GAME_URL", "Process"))
    return (-not [string]::IsNullOrWhiteSpace($token)) -and ((-not [string]::IsNullOrWhiteSpace($publicBaseUrl)) -or (-not [string]::IsNullOrWhiteSpace($publicGameUrl)))
}

function Get-NamedTunnelMissingConfigText {
    $missing = New-Object System.Collections.Generic.List[string]
    $token = [string]([Environment]::GetEnvironmentVariable("CLOUDFLARE_TUNNEL_TOKEN", "Process"))
    $publicBaseUrl = [string]([Environment]::GetEnvironmentVariable("PUBLIC_BASE_URL", "Process"))
    $publicGameUrl = [string]([Environment]::GetEnvironmentVariable("PUBLIC_GAME_URL", "Process"))
    if ([string]::IsNullOrWhiteSpace($token)) {
        $missing.Add("CLOUDFLARE_TUNNEL_TOKEN") | Out-Null
    }
    if ([string]::IsNullOrWhiteSpace($publicBaseUrl) -and [string]::IsNullOrWhiteSpace($publicGameUrl)) {
        $missing.Add("PUBLIC_BASE_URL or PUBLIC_GAME_URL") | Out-Null
    }
    return ($missing -join ", ")
}

function Normalize-GameUrl([string]$Url) {
    if ([string]::IsNullOrWhiteSpace($Url)) { return $null }
    $trimmed = $Url.Trim().TrimEnd("/")
    if ($trimmed.ToLowerInvariant().EndsWith("/game")) {
        return $trimmed
    }
    return $trimmed + "/Game"
}

function Normalize-BaseUrl([string]$BaseUrl, [string]$GameUrl) {
    if (-not [string]::IsNullOrWhiteSpace($BaseUrl)) {
        $base = $BaseUrl.Trim().TrimEnd("/")
        if ($base.ToLowerInvariant().EndsWith("/game")) {
            return $base.Substring(0, $base.Length - 5)
        }
        return $base
    }
    if ([string]::IsNullOrWhiteSpace($GameUrl)) {
        return $null
    }
    $game = $GameUrl.Trim().TrimEnd("/")
    if ($game.ToLowerInvariant().EndsWith("/game")) {
        return $game.Substring(0, $game.Length - 5)
    }
    try {
        $uri = [Uri]$game
        return $uri.GetLeftPart([System.UriPartial]::Authority)
    } catch {
        return $null
    }
}

function Get-UrlHost([string]$Url) {
    if ([string]::IsNullOrWhiteSpace($Url)) { return $null }
    try {
        $u = [Uri]$Url
        return $u.Host
    } catch {
        return $null
    }
}

function Test-HostResolvableOnPublicDns([string]$HostName) {
    if ([string]::IsNullOrWhiteSpace($HostName)) { return $false }
    $dnsServers = @("1.1.1.1", "8.8.8.8")
    foreach ($server in $dnsServers) {
        try {
            $answer = Resolve-DnsName -Name $HostName -Server $server -ErrorAction Stop | Select-Object -First 1
            if ($answer) {
                return $true
            }
        } catch {
        }
    }
    return $false
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
Require-Script $namedTunnelStartScript
Require-Script $stopQuickTunnelScript
Require-Script $stopNamedTunnelScript
if (-not $SkipBootstrap) {
    Require-Script $bootstrapScript
}

Push-Location $repoRoot
try {
    Load-EnvFile -Path (Join-Path $repoRoot $AppEnvFile)

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

    # Reload env in case bootstrap updated/created the target env file.
    Load-EnvFile -Path (Join-Path $repoRoot $AppEnvFile)

    Write-Output "STEP=START_APP"
    $appScriptOutput = @()
    try {
        if ($AutoBuild) {
            $appScriptOutput = & $appStartScript -AutoBuild -Port $Port -EnvFile $AppEnvFile -EnableH2Fallback 2>&1
        } else {
            $appScriptOutput = & $appStartScript -Port $Port -EnvFile $AppEnvFile -EnableH2Fallback 2>&1
        }
    } catch {
        throw ("Khoi dong app that bai: {0}" -f $_.Exception.Message)
    }
    $appScriptOutput | ForEach-Object { Write-Output ([string]$_) }

    $localGameUrl = "http://127.0.0.1:$Port/Game"
    $localPingUrl = "http://127.0.0.1:$Port/Game/api/connectivity/ping"
    $localWsInfoUrl = "http://127.0.0.1:$Port/Game/ws/info?t=1"
    $localAppOk = Wait-HttpOk -Url $localPingUrl -TimeoutSeconds $WaitSeconds
    $localWsOk = Wait-HttpOk -Url $localWsInfoUrl -TimeoutSeconds 10

    if (-not $localAppOk) {
        throw "App local khong san sang tai $localPingUrl"
    }
    if (-not $localWsOk) {
        throw "WebSocket endpoint local khong san sang tai $localWsInfoUrl"
    }

    $resolvedTunnelMode = $TunnelMode
    if ($TunnelMode -eq "auto") {
        if (Has-NamedTunnelConfig) {
            $resolvedTunnelMode = "named"
        } else {
            $resolvedTunnelMode = "quick"
            $missing = Get-NamedTunnelMissingConfigText
            if (-not [string]::IsNullOrWhiteSpace($missing)) {
                Write-Warning ("Named tunnel chua du cau hinh ({0}). Dang fallback quick tunnel." -f $missing)
            }
        }
    }

    $publicBaseUrl = $null
    $publicGameUrl = $null
    $tunnelLogErr = $null

    if ($resolvedTunnelMode -eq "named") {
        Write-Output "STEP=START_TUNNEL_NAMED"
        & $stopQuickTunnelScript | Out-Null
        try {
            $namedOutput = & $namedTunnelStartScript -EnvFile $AppEnvFile -WaitSeconds ([Math]::Max(20, $WaitSeconds)) 2>&1
            $namedOutput | ForEach-Object { Write-Output ([string]$_) }

            $namedMap = Parse-KeyValueLines -Lines $namedOutput
            if ($namedMap.ContainsKey("PUBLIC_BASE_URL")) {
                $publicBaseUrl = [string]$namedMap["PUBLIC_BASE_URL"]
            }
            if ($namedMap.ContainsKey("PUBLIC_GAME_URL")) {
                $publicGameUrl = [string]$namedMap["PUBLIC_GAME_URL"]
            }
            $publicGameUrl = Normalize-GameUrl -Url $publicGameUrl
            if ([string]::IsNullOrWhiteSpace($publicGameUrl) -and -not [string]::IsNullOrWhiteSpace($publicBaseUrl)) {
                $publicGameUrl = Normalize-GameUrl -Url $publicBaseUrl
            }
            $publicBaseUrl = Normalize-BaseUrl -BaseUrl $publicBaseUrl -GameUrl $publicGameUrl
            if ([string]::IsNullOrWhiteSpace($publicGameUrl)) {
                throw "Named tunnel da chay nhung chua co PUBLIC_BASE_URL/PUBLIC_GAME_URL. Hay cap nhat $AppEnvFile."
            }
            $tunnelLogErr = $namedCfErrLog
        } catch {
            if ($TunnelMode -eq "auto") {
                Write-Warning ("Named tunnel loi: {0}" -f $_.Exception.Message)
                Write-Warning "Auto mode se fallback sang quick tunnel."
                & $stopNamedTunnelScript | Out-Null
                $resolvedTunnelMode = "quick"
            } else {
                throw
            }
        }
    }

    if ($resolvedTunnelMode -eq "quick") {
        Write-Output "STEP=START_TUNNEL_QUICK"
        & $stopNamedTunnelScript | Out-Null
        $quickOutput = @()
        try {
            $quickOutput = & $tunnelStartScript -LocalPort $Port -ContextPath "/Game" 2>&1
        } catch {
            throw ("Khoi dong quick tunnel that bai: {0}" -f $_.Exception.Message)
        }
        $quickOutput | ForEach-Object { Write-Output ([string]$_) }

        $publicBaseUrl = Get-QuickTunnelBaseUrl -LogPath $cfErrLog
        if (-not $publicBaseUrl) {
            throw "Khong lay duoc quick tunnel URL tu $cfErrLog"
        }
        $publicBaseUrl = Normalize-BaseUrl -BaseUrl $publicBaseUrl -GameUrl $null
        $publicGameUrl = Normalize-GameUrl -Url $publicBaseUrl
        $tunnelLogErr = $cfErrLog
    }

    $publicPingUrl = $publicGameUrl.TrimEnd("/") + "/api/connectivity/ping"
    $publicWsInfoUrl = $publicGameUrl.TrimEnd("/") + "/ws/info?t=1"

    $publicProbeTimeoutSeconds = [Math]::Max($WaitSeconds, 75)
    $publicGameOk = Wait-HttpOk -Url $publicPingUrl -TimeoutSeconds $publicProbeTimeoutSeconds
    $publicWsOk = Wait-HttpOk -Url $publicWsInfoUrl -TimeoutSeconds ([Math]::Max(20, [Math]::Min(120, $publicProbeTimeoutSeconds + 15)))
    $publicPageOk = Wait-HttpOk -Url $publicGameUrl -TimeoutSeconds 12
    $tunnelPidCheckFile = if ($resolvedTunnelMode -eq "named") { $namedTunnelPidFile } else { $tunnelPidFile }
    $tunnelAlive = Test-ProcessAliveByPidFile -PidFilePath $tunnelPidCheckFile
    $appAlive = Test-ProcessAliveByPidFile -PidFilePath $appPidFile
    $publicHost = Get-UrlHost -Url $publicGameUrl
    $publicDnsOkOnPublicResolvers = Test-HostResolvableOnPublicDns -HostName $publicHost
    $allowQuickDnsBypass = ($resolvedTunnelMode -eq "quick") -and $tunnelAlive -and $appAlive -and $publicDnsOkOnPublicResolvers

    if (-not $publicGameOk) {
        if ($allowQuickDnsBypass) {
            Write-Warning "Quick tunnel URL chua truy cap duoc tren DNS hien tai cua may chu, nhung da resolve duoc qua public DNS (1.1.1.1/8.8.8.8). Van tiep tuc."
            $publicGameOk = $true
        } else {
            $hint = ""
            if ($tunnelAlive -and $appAlive) {
                $hint = " (tunnel/app van dang chay; co the edge chua on dinh, thu lai sau 10-30s)"
            }
            if ($resolvedTunnelMode -eq "quick") {
                $hint += " Neu mang cong cong bi chan *.trycloudflare.com, hay cau hinh CLOUDFLARE_TUNNEL_TOKEN + PUBLIC_BASE_URL de dung named tunnel domain co dinh."
            }
            throw ("Tunnel mode '{0}' da tao nhung URL public khong truy cap duoc: {1} (probe: {2}){3}" -f $resolvedTunnelMode, $publicGameUrl, $publicPingUrl, $hint)
        }
    }
    if (-not $publicWsOk) {
        if ($allowQuickDnsBypass) {
            Write-Warning "Khong probe duoc public ws/info bang DNS hien tai cua may chu, bo qua check nay vi quick tunnel da resolve qua public DNS."
            $publicWsOk = $true
        } else {
            throw "Tunnel URL public truy cap duoc web nhung endpoint ws/info chua san sang: $publicWsInfoUrl"
        }
    }

    @(
        "# Auto-generated by scripts/start-remote-public-session.ps1"
        "TUNNEL_MODE=$resolvedTunnelMode"
        "PUBLIC_GAME_URL=$publicGameUrl"
        "PUBLIC_BASE_URL=$publicBaseUrl"
        "LOCAL_GAME_URL=$localGameUrl"
        "UPDATED_AT_UTC=$(([DateTime]::UtcNow).ToString('yyyy-MM-ddTHH:mm:ssZ'))"
    ) | Set-Content -Path $publicUrlFilePath -Encoding UTF8

    Write-Output ""
    Write-Output "========================================"
    Write-Output " CONG KHAI SAN SANG - GUI LINK NAY ($resolvedTunnelMode)"
    Write-Output " $publicGameUrl"
    Write-Output "========================================"
    Write-Output ""
    Write-Output "SESSION_READY=1"
    Write-Output "TUNNEL_MODE=$resolvedTunnelMode"
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
    Write-Output "TUNNEL_LOG_ERR=$tunnelLogErr"
    Write-Output "PUBLIC_URL_FILE=$publicUrlFilePath"
    Write-Output "APP_ENV_FILE=$AppEnvFile"

    if ($OpenBrowser) {
        Start-Process $publicGameUrl | Out-Null
    }
} finally {
    Pop-Location
}
