param(
    [switch]$AutoBuild,
    [int]$Port = 8080,
    [int]$WaitSeconds = 30,
    [string]$PublicUrlFile = "public-game-url.txt",
    [string]$AppEnvFile = ".env.public.local",
    [ValidateSet("auto", "named", "quick", "runlocal", "localtunnel")]
    [string]$TunnelMode = "auto",
    [switch]$OpenBrowser,
    [switch]$SkipBootstrap,
    [switch]$ForceBootstrap,
    [ValidateSet("auto", "h2", "mysql", "postgres")]
    [string]$BootstrapDb = "auto"
)

$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$appStartScript = Join-Path $PSScriptRoot "start-prod-app.ps1"
$tunnelStartScript = Join-Path $PSScriptRoot "start-remote-quick-tunnel.ps1"
$namedTunnelStartScript = Join-Path $PSScriptRoot "start-cloudflare-named-tunnel.ps1"
$fallbackTunnelStartScript = Join-Path $PSScriptRoot "start-fallback-public-tunnel.ps1"
$stopQuickTunnelScript = Join-Path $PSScriptRoot "stop-remote-quick-tunnel.ps1"
$stopNamedTunnelScript = Join-Path $PSScriptRoot "stop-cloudflare-named-tunnel.ps1"
$stopFallbackTunnelScript = Join-Path $PSScriptRoot "stop-fallback-public-tunnel.ps1"
$cfErrLog = Join-Path $repoRoot "cloudflared.err.log"
$namedCfErrLog = Join-Path $repoRoot "cloudflared-named.err.log"
$fallbackTunnelErrLog = Join-Path $repoRoot "public-fallback-tunnel.err.log"
$appOutLog = Join-Path $repoRoot "run-prod-public.out.log"
$appErrLog = Join-Path $repoRoot "run-prod-public.err.log"
$appPidFile = Join-Path $repoRoot "app-prod.pid"
$tunnelPidFile = Join-Path $repoRoot "cloudflared.pid"
$namedTunnelPidFile = Join-Path $repoRoot "cloudflared-named.pid"
$publicUrlFilePath = if ([System.IO.Path]::IsPathRooted($PublicUrlFile)) { $PublicUrlFile } else { Join-Path $repoRoot $PublicUrlFile }
$bootstrapScript = Join-Path $scriptsRoot "dev-env-bootstrap.ps1"
$statusScript = Join-Path $PSScriptRoot "print-runtime-status.ps1"

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

function Invoke-HttpProbe([string]$Url,
                          [string]$AcceptHeader = "",
                          [string]$ExpectedBodyText = "",
                          [int]$TimeoutSeconds = 6) {
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($AcceptHeader)) {
        $headers["Accept"] = $AcceptHeader
    }
    # Browser-like UA helps avoid edge-specific behavior differences on public HTML routes.
    $headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

    try {
        $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSeconds -Headers $headers -MaximumRedirection 5
        $body = [string]$res.Content
        $statusOk = ($res.StatusCode -ge 200 -and $res.StatusCode -lt 400)
        $bodyOk = [string]::IsNullOrWhiteSpace($ExpectedBodyText) -or (($body -like ("*" + $ExpectedBodyText + "*")))
        $bodySnippet = if ([string]::IsNullOrWhiteSpace($body)) {
            ""
        } elseif ($body.Length -gt 200) {
            $body.Substring(0, 200)
        } else {
            $body
        }
        return @{
            ok = ($statusOk -and $bodyOk)
            statusCode = [int]$res.StatusCode
            bodyMatched = $bodyOk
            bodySnippet = ($bodySnippet -replace "\s+", " ").Trim()
            error = ""
        }
    } catch {
        $statusCode = 0
        try {
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
        } catch {
        }
        return @{
            ok = $false
            statusCode = $statusCode
            bodyMatched = $false
            bodySnippet = ""
            error = $_.Exception.Message
        }
    }
}

function Wait-HttpProbe([string]$Url,
                        [int]$TimeoutSeconds,
                        [string]$AcceptHeader = "",
                        [string]$ExpectedBodyText = "") {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastProbe = @{
        ok = $false
        statusCode = 0
        bodyMatched = $false
        bodySnippet = ""
        error = "No probe executed"
    }
    while ((Get-Date) -lt $deadline) {
        $lastProbe = Invoke-HttpProbe -Url $Url -AcceptHeader $AcceptHeader -ExpectedBodyText $ExpectedBodyText
        if ($lastProbe.ok) {
            return $lastProbe
        }
        Start-Sleep -Milliseconds 900
    }
    return $lastProbe
}

function Format-ProbeDiagnostic([hashtable]$Probe, [string]$Url) {
    if ($null -eq $Probe) {
        return ("url={0}; status=unknown; error=no-probe" -f $Url)
    }
    $parts = New-Object System.Collections.Generic.List[string]
    $parts.Add(("url={0}" -f $Url)) | Out-Null
    if ($Probe.ContainsKey("statusCode") -and [int]$Probe["statusCode"] -gt 0) {
        $parts.Add(("status={0}" -f $Probe["statusCode"])) | Out-Null
    }
    if ($Probe.ContainsKey("error") -and -not [string]::IsNullOrWhiteSpace([string]$Probe["error"])) {
        $parts.Add(("error={0}" -f ([string]$Probe["error"]))) | Out-Null
    }
    if ($Probe.ContainsKey("bodyMatched")) {
        $parts.Add(("bodyMatched={0}" -f ([int][bool]$Probe["bodyMatched"]))) | Out-Null
    }
    if ($Probe.ContainsKey("bodySnippet") -and -not [string]::IsNullOrWhiteSpace([string]$Probe["bodySnippet"])) {
        $parts.Add(("body={0}" -f ([string]$Probe["bodySnippet"]))) | Out-Null
    }
    return ($parts -join "; ")
}

function Test-CommandExists([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
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

function Test-FallbackTunnelAvailable {
    return (Test-CommandExists "npx") -or (Test-CommandExists "npx.cmd")
}

function Start-FallbackTunnelAndResolve([int]$Port, [string]$SelectedProvider) {
    Write-Output "STEP=START_TUNNEL_FALLBACK"
    & $stopQuickTunnelScript | Out-Null
    & $stopNamedTunnelScript | Out-Null
    $fallbackArgs = @{
        LocalPort = $Port
        ContextPath = "/Game"
    }
    if (-not [string]::IsNullOrWhiteSpace($SelectedProvider)) {
        $fallbackArgs.Provider = $SelectedProvider
    }
    $fallbackOutput = & $fallbackTunnelStartScript @fallbackArgs 2>&1
    $fallbackOutput | ForEach-Object { Write-Output ([string]$_) }
    $fallbackMap = Parse-KeyValueLines -Lines $fallbackOutput
    if (-not $fallbackMap.ContainsKey("PUBLIC_GAME_URL")) {
        throw "Fallback provider khong tao duoc PUBLIC_GAME_URL."
    }
    $resolvedProvider = if ($fallbackMap.ContainsKey("TUNNEL_MODE")) {
        [string]$fallbackMap["TUNNEL_MODE"]
    } else {
        [string]$SelectedProvider
    }
    return @{
        publicGameUrl = Normalize-GameUrl -Url ([string]$fallbackMap["PUBLIC_GAME_URL"])
        publicBaseUrl = Normalize-BaseUrl -BaseUrl ([string]$fallbackMap["PUBLIC_BASE_URL"]) -GameUrl ([string]$fallbackMap["PUBLIC_GAME_URL"])
        tunnelMode = $resolvedProvider
        tunnelLogErr = $fallbackTunnelErrLog
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

function Test-HostResolvableLocally([string]$HostName) {
    if ([string]::IsNullOrWhiteSpace($HostName)) { return $false }
    try {
        $addresses = [System.Net.Dns]::GetHostAddresses($HostName)
        return ($addresses.Count -gt 0)
    } catch {
        return $false
    }
}

function Wait-HostResolvableOnPublicDns([string]$HostName, [int]$TimeoutSeconds, [int]$ProbeIntervalMs = 1500) {
    if ([string]::IsNullOrWhiteSpace($HostName)) { return $false }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        if (Test-HostResolvableOnPublicDns -HostName $HostName) {
            return $true
        }
        if (($attempt -eq 1) -or (($attempt % 5) -eq 0)) {
            Write-Warning ("Dang cho DNS public resolve host {0}..." -f $HostName)
        }
        Start-Sleep -Milliseconds $ProbeIntervalMs
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
Require-Script $fallbackTunnelStartScript
Require-Script $stopQuickTunnelScript
Require-Script $stopNamedTunnelScript
Require-Script $stopFallbackTunnelScript
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
            $appScriptOutput = & $appStartScript -AutoBuild -Port $Port -EnvFile $AppEnvFile -EnableH2Fallback -WaitSeconds ([Math]::Max(45, $WaitSeconds)) 2>&1
        } else {
            $appScriptOutput = & $appStartScript -Port $Port -EnvFile $AppEnvFile -EnableH2Fallback -WaitSeconds ([Math]::Max(45, $WaitSeconds)) 2>&1
        }
    } catch {
        throw ("Khoi dong app that bai: {0}" -f $_.Exception.Message)
    }
    $appScriptOutput | ForEach-Object { Write-Output ([string]$_) }

    $localGameUrl = "http://127.0.0.1:$Port/Game"
    $localPingUrl = "http://127.0.0.1:$Port/Game/api/connectivity/ping"
    $localWsInfoUrl = "http://127.0.0.1:$Port/Game/ws/info?t=1"
    $localPageUrl = $localGameUrl
    $localAppOk = Wait-HttpOk -Url $localPingUrl -TimeoutSeconds $WaitSeconds
    $localWsOk = Wait-HttpOk -Url $localWsInfoUrl -TimeoutSeconds 10
    $pageMarker = 'name="app-context-path"'
    $localPageProbe = Wait-HttpProbe -Url $localPageUrl -TimeoutSeconds 20 -AcceptHeader "text/html,application/xhtml+xml" -ExpectedBodyText $pageMarker
    $localPageOk = [bool]$localPageProbe.ok

    if (-not $localAppOk) {
        throw "App local khong san sang tai $localPingUrl"
    }
    if (-not $localWsOk) {
        throw "WebSocket endpoint local khong san sang tai $localWsInfoUrl"
    }
    if (-not $localPageOk) {
        throw ("Trang landing local bi loi hoac khong truy cap duoc: {0} ({1})" -f $localPageUrl, (Format-ProbeDiagnostic -Probe $localPageProbe -Url $localPageUrl))
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

    if (($resolvedTunnelMode -eq "runlocal") -or ($resolvedTunnelMode -eq "localtunnel")) {
        $fallbackResult = Start-FallbackTunnelAndResolve -Port $Port -SelectedProvider $resolvedTunnelMode
        $publicGameUrl = [string]$fallbackResult.publicGameUrl
        $publicBaseUrl = [string]$fallbackResult.publicBaseUrl
        $resolvedTunnelMode = [string]$fallbackResult.tunnelMode
        $tunnelLogErr = [string]$fallbackResult.tunnelLogErr
    }

    if ($resolvedTunnelMode -eq "named") {
        Write-Output "STEP=START_TUNNEL_NAMED"
        & $stopQuickTunnelScript | Out-Null
        & $stopFallbackTunnelScript | Out-Null
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
        & $stopFallbackTunnelScript | Out-Null
        $quickMaxAttempts = 3
        $quickAttempt = 0
        $quickLastError = $null
        while ($quickAttempt -lt $quickMaxAttempts) {
            $quickAttempt++
            Write-Output ("QUICK_TUNNEL_ATTEMPT={0}/{1}" -f $quickAttempt, $quickMaxAttempts)
            & $stopQuickTunnelScript | Out-Null

            $quickOutput = @()
            try {
                $quickOutput = & $tunnelStartScript -LocalPort $Port -ContextPath "/Game" 2>&1
            } catch {
                $quickLastError = "Khoi dong quick tunnel that bai: $($_.Exception.Message)"
                if ($quickAttempt -lt $quickMaxAttempts) {
                    Write-Warning ($quickLastError + " Dang thu lai...")
                    Start-Sleep -Seconds 2
                    continue
                }
                break
            }
            $quickOutput | ForEach-Object { Write-Output ([string]$_) }

            $publicBaseUrl = Get-QuickTunnelBaseUrl -LogPath $cfErrLog
            $quickAliveAfterStart = Test-ProcessAliveByPidFile -PidFilePath $tunnelPidFile

            if ([string]::IsNullOrWhiteSpace($publicBaseUrl)) {
                $quickLastError = "Khong lay duoc quick tunnel URL tu $cfErrLog"
            } elseif (-not $quickAliveAfterStart) {
                $quickLastError = "Quick tunnel vua khoi dong nhung process da dung."
            } else {
                $publicBaseUrl = Normalize-BaseUrl -BaseUrl $publicBaseUrl -GameUrl $null
                $publicGameUrl = Normalize-GameUrl -Url $publicBaseUrl
                $tunnelLogErr = $cfErrLog
                break
            }

            if ($quickAttempt -lt $quickMaxAttempts) {
                Write-Warning ($quickLastError + " Dang thu lai...")
                Start-Sleep -Seconds 2
            }
        }

        if ([string]::IsNullOrWhiteSpace($publicGameUrl)) {
            Write-Warning ("Quick tunnel khong on dinh sau {0} lan thu. Dang thu fallback provider..." -f $quickMaxAttempts)
            if (-not (Test-FallbackTunnelAvailable)) {
                throw ("Quick tunnel that bai va fallback provider cung khong tao duoc public URL. Loi quick tunnel cuoi: {0}" -f $quickLastError)
            }
            $fallbackResult = Start-FallbackTunnelAndResolve -Port $Port -SelectedProvider "auto"
            $publicGameUrl = [string]$fallbackResult.publicGameUrl
            $publicBaseUrl = [string]$fallbackResult.publicBaseUrl
            $resolvedTunnelMode = [string]$fallbackResult.tunnelMode
            $tunnelLogErr = [string]$fallbackResult.tunnelLogErr
        }
    }

    $publicPingUrl = $publicGameUrl.TrimEnd("/") + "/api/connectivity/ping"
    $publicWsInfoUrl = $publicGameUrl.TrimEnd("/") + "/ws/info?t=1"
    $publicHost = Get-UrlHost -Url $publicGameUrl
    $publicDnsWaitSeconds = if ($resolvedTunnelMode -eq "quick") {
        [Math]::Max(20, [Math]::Min(120, $WaitSeconds + 45))
    } else {
        10
    }
    $publicDnsOkOnPublicResolvers = $true
    $publicDnsOkOnSystemResolver = $true

    if (-not [string]::IsNullOrWhiteSpace($publicHost)) {
        Write-Output "PUBLIC_HOST=$publicHost"
        if ($resolvedTunnelMode -eq "quick") {
            Write-Output "STEP=WAIT_PUBLIC_DNS"
            $publicDnsOkOnPublicResolvers = Wait-HostResolvableOnPublicDns -HostName $publicHost -TimeoutSeconds $publicDnsWaitSeconds
        } else {
            $publicDnsOkOnPublicResolvers = Test-HostResolvableOnPublicDns -HostName $publicHost
        }
        $publicDnsOkOnSystemResolver = Test-HostResolvableLocally -HostName $publicHost
        Write-Output "PUBLIC_DNS_PUBLIC_OK=$([int]$publicDnsOkOnPublicResolvers)"
        Write-Output "PUBLIC_DNS_LOCAL_OK=$([int]$publicDnsOkOnSystemResolver)"
    }

    $publicProbeTimeoutSeconds = [Math]::Max($WaitSeconds, 75)
    $publicGameOk = Wait-HttpOk -Url $publicPingUrl -TimeoutSeconds $publicProbeTimeoutSeconds
    $publicWsOk = Wait-HttpOk -Url $publicWsInfoUrl -TimeoutSeconds ([Math]::Max(20, [Math]::Min(120, $publicProbeTimeoutSeconds + 15)))
    $publicPageProbeTimeoutSeconds = [Math]::Max(30, [Math]::Min(150, $publicProbeTimeoutSeconds + 20))
    $publicPageProbe = Wait-HttpProbe -Url $publicGameUrl -TimeoutSeconds $publicPageProbeTimeoutSeconds -AcceptHeader "text/html,application/xhtml+xml" -ExpectedBodyText $pageMarker
    $publicPageOk = [bool]$publicPageProbe.ok
    $tunnelPidCheckFile = switch ($resolvedTunnelMode) {
        "named" { $namedTunnelPidFile }
        "quick" { $tunnelPidFile }
        default { Join-Path $repoRoot "public-fallback-tunnel.pid" }
    }
    $tunnelAlive = Test-ProcessAliveByPidFile -PidFilePath $tunnelPidCheckFile
    $appAlive = Test-ProcessAliveByPidFile -PidFilePath $appPidFile
    $allowQuickDnsBypass = ($resolvedTunnelMode -eq "quick") -and $tunnelAlive -and $appAlive -and $publicDnsOkOnPublicResolvers

    if (($resolvedTunnelMode -eq "quick") -and (-not $publicDnsOkOnPublicResolvers)) {
        $dnsHint = "Hay doi them 10-30s roi chay lai. Neu can on dinh hon, cau hinh CLOUDFLARE_TUNNEL_TOKEN + PUBLIC_BASE_URL de dung named tunnel."
        if ($tunnelAlive -and $appAlive) {
            $dnsHint = "Tunnel/app dang chay nhung DNS public cua quick tunnel chua resolve. " + $dnsHint
        }
        throw ("Quick tunnel da tao URL nhung DNS public chua resolve: {0} (host: {1}). {2}" -f $publicGameUrl, $publicHost, $dnsHint)
    }

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
            } elseif (($resolvedTunnelMode -eq "runlocal") -or ($resolvedTunnelMode -eq "localtunnel")) {
                $hint += " Fallback provider da tao URL nhung public probe that bai; hay xem public-fallback-tunnel.err.log."
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
    if (-not $publicPageOk) {
        $pageHint = ""
        if ($resolvedTunnelMode -eq "quick" -and $tunnelAlive -and $appAlive) {
            $pageHint = " Quick tunnel/app van dang chay; day thuong la warm-up/edge delay, khong phai app da chet."
        }
        throw ("Tunnel URL public da tao nhung landing page khong render duoc: {0} ({1}){2}" -f $publicGameUrl, (Format-ProbeDiagnostic -Probe $publicPageProbe -Url $publicGameUrl), $pageHint)
    }

    $publicUrlLines = @(
        "# Auto-generated by scripts/runtime/start-remote-public-session.ps1"
        "TUNNEL_MODE=$resolvedTunnelMode"
        "PUBLIC_GAME_URL=$publicGameUrl"
        "PUBLIC_BASE_URL=$publicBaseUrl"
        "LOCAL_GAME_URL=$localGameUrl"
        "UPDATED_AT_UTC=$(([DateTime]::UtcNow).ToString('yyyy-MM-ddTHH:mm:ssZ'))"
    )
    [System.IO.File]::WriteAllLines($publicUrlFilePath, $publicUrlLines, [System.Text.Encoding]::ASCII)

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
    Write-Output "PUBLIC_HOST=$publicHost"
    Write-Output "PUBLIC_PING_URL=$publicPingUrl"
    Write-Output "LOCAL_GAME_OK=$([int]$localAppOk)"
    Write-Output "LOCAL_WS_OK=$([int]$localWsOk)"
    Write-Output "LOCAL_PAGE_OK=$([int]$localPageOk)"
    Write-Output "PUBLIC_DNS_PUBLIC_OK=$([int]$publicDnsOkOnPublicResolvers)"
    Write-Output "PUBLIC_DNS_LOCAL_OK=$([int]$publicDnsOkOnSystemResolver)"
    Write-Output "PUBLIC_GAME_OK=$([int]$publicGameOk)"
    Write-Output "PUBLIC_WS_OK=$([int]$publicWsOk)"
    Write-Output "PUBLIC_PAGE_OK=$([int]$publicPageOk)"
    Write-Output "APP_LOG_OUT=$appOutLog"
    Write-Output "APP_LOG_ERR=$appErrLog"
    Write-Output "TUNNEL_LOG_ERR=$tunnelLogErr"
    Write-Output "PUBLIC_URL_FILE=$publicUrlFilePath"
    Write-Output "APP_ENV_FILE=$AppEnvFile"

    if (Test-Path $statusScript) {
        Write-Output ""
        & $statusScript -Title "PUBLIC SESSION STATUS" -NoHints | ForEach-Object { Write-Output ([string]$_) }
    }

    if ($OpenBrowser) {
        Start-Process $publicGameUrl | Out-Null
    }
} finally {
    Pop-Location
}
