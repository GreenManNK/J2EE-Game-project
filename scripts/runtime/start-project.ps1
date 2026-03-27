param(
    [switch]$ForceBootstrap,
    [switch]$SkipBootstrap
)

$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$bootstrapScript = Join-Path $scriptsRoot "dev-env-bootstrap.ps1"
$publicStartScript = Join-Path $PSScriptRoot "start-remote-public-session.ps1"

function Test-CommandExists([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-CloudflaredCommandPath {
    $command = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    if ($env:OS -eq "Windows_NT") {
        $localTool = Join-Path $repoRoot ".tools\cloudflared.exe"
        if (Test-Path $localTool) {
            return $localTool
        }
    }

    return $null
}

function Test-BuildToolAvailable {
    foreach ($relativePath in @("mvnw.cmd", "mvnw", "gradlew.bat", "gradlew")) {
        if (Test-Path (Join-Path $repoRoot $relativePath)) {
            return $true
        }
    }

    return (Test-CommandExists "mvn") -or (Test-CommandExists "gradle")
}

function Test-FallbackTunnelAvailable {
    return (Test-CommandExists "npx") -or (Test-CommandExists "npx.cmd")
}

function Read-EnvFileMap([string]$RelativePath) {
    $map = @{}
    $fullPath = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path $fullPath)) {
        return $map
    }

    foreach ($line in Get-Content $fullPath -ErrorAction SilentlyContinue) {
        $text = [string]$line
        if ([string]::IsNullOrWhiteSpace($text)) { continue }
        if ($text.Trim().StartsWith("#")) { continue }
        $index = $text.IndexOf("=")
        if ($index -lt 1) { continue }
        $key = $text.Substring(0, $index).Trim()
        $value = $text.Substring($index + 1)
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            $map[$key] = $value
        }
    }

    return $map
}

function Get-FirstNonEmpty([string[]]$Values, [string]$DefaultValue = "") {
    foreach ($value in $Values) {
        if (-not [string]::IsNullOrWhiteSpace([string]$value)) {
            return [string]$value
        }
    }
    return $DefaultValue
}

function Normalize-DbKind([string]$Value) {
    $normalized = [string]$Value
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        return "auto"
    }

    switch ($normalized.Trim().ToLowerInvariant()) {
        "postgresql" { return "postgres" }
        "postgres" { return "postgres" }
        "mysql" { return "mysql" }
        "h2" { return "h2" }
        default { return "auto" }
    }
}

function Test-TcpReachable([string]$HostName, [string]$PortText) {
    $port = 0
    if ([string]::IsNullOrWhiteSpace($HostName)) {
        return $false
    }
    if (-not [int]::TryParse(([string]$PortText), [ref]$port)) {
        return $false
    }

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(1200, $false)) {
            return $false
        }
        $client.EndConnect($async) | Out-Null
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Resolve-DbSelection {
    $sharedEnv = Read-EnvFileMap ".env.public.local"
    $mysqlEnv = Read-EnvFileMap ".env.public.mysql.local"
    $postgresEnv = Read-EnvFileMap ".env.public.postgres.local"
    $processDbKind = Normalize-DbKind ([Environment]::GetEnvironmentVariable("APP_DATASOURCE_KIND", "Process"))

    if ($processDbKind -eq "postgres") {
        return @{
            kind = "postgres"
            reason = "Dung DB theo APP_DATASOURCE_KIND trong moi truong hien tai"
            envFile = ".env.public.local"
            overlayEnvFile = ".env.public.postgres.local"
            explicit = $false
        }
    }

    if ($processDbKind -eq "mysql") {
        return @{
            kind = "mysql"
            reason = "Dung DB theo APP_DATASOURCE_KIND trong moi truong hien tai"
            envFile = ".env.public.local"
            overlayEnvFile = if (Test-Path (Join-Path $repoRoot ".env.public.mysql.local")) { ".env.public.mysql.local" } else { "" }
            explicit = $false
        }
    }

    if ($processDbKind -eq "h2") {
        return @{
            kind = "h2"
            reason = "Dung DB theo APP_DATASOURCE_KIND trong moi truong hien tai"
            envFile = ".env.public.local"
            overlayEnvFile = ""
            explicit = $false
        }
    }

    $postgresHost = Get-FirstNonEmpty @(
        [Environment]::GetEnvironmentVariable("APP_DATASOURCE_POSTGRES_HOST", "Process"),
        $postgresEnv["APP_DATASOURCE_POSTGRES_HOST"],
        "127.0.0.1"
    )
    $postgresPort = Get-FirstNonEmpty @(
        [Environment]::GetEnvironmentVariable("APP_DATASOURCE_POSTGRES_PORT", "Process"),
        $postgresEnv["APP_DATASOURCE_POSTGRES_PORT"],
        "5432"
    )
    $postgresConfigured = ($postgresEnv.Count -gt 0) -or
        (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("APP_DATASOURCE_POSTGRES_DATABASE", "Process"))) -or
        (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("APP_DATASOURCE_POSTGRES_USERNAME", "Process")))
    if ($postgresConfigured -and (Test-TcpReachable -HostName $postgresHost -PortText $postgresPort)) {
        return @{
            kind = "postgres"
            reason = "Phat hien cau hinh PostgreSQL va cong dich vu dang truy cap duoc"
            envFile = ".env.public.local"
            overlayEnvFile = ".env.public.postgres.local"
            explicit = $false
        }
    }

    $mysqlHost = Get-FirstNonEmpty @(
        [Environment]::GetEnvironmentVariable("APP_DATASOURCE_MYSQL_HOST", "Process"),
        [Environment]::GetEnvironmentVariable("LARAGON_DB_HOST", "Process"),
        $mysqlEnv["APP_DATASOURCE_MYSQL_HOST"],
        $mysqlEnv["LARAGON_DB_HOST"],
        $sharedEnv["APP_DATASOURCE_MYSQL_HOST"],
        $sharedEnv["LARAGON_DB_HOST"],
        "127.0.0.1"
    )
    $mysqlPort = Get-FirstNonEmpty @(
        [Environment]::GetEnvironmentVariable("APP_DATASOURCE_MYSQL_PORT", "Process"),
        [Environment]::GetEnvironmentVariable("LARAGON_DB_PORT", "Process"),
        $mysqlEnv["APP_DATASOURCE_MYSQL_PORT"],
        $mysqlEnv["LARAGON_DB_PORT"],
        $sharedEnv["APP_DATASOURCE_MYSQL_PORT"],
        $sharedEnv["LARAGON_DB_PORT"],
        "3306"
    )
    if (Test-TcpReachable -HostName $mysqlHost -PortText $mysqlPort) {
        return @{
            kind = "mysql"
            reason = "Phat hien cong MySQL/Laragon dang truy cap duoc"
            envFile = ".env.public.local"
            overlayEnvFile = if (Test-Path (Join-Path $repoRoot ".env.public.mysql.local")) { ".env.public.mysql.local" } else { "" }
            explicit = $false
        }
    }

    return @{
        kind = "h2"
        reason = "Khong phat hien MySQL/PostgreSQL kha dung, fallback ve H2 local"
        envFile = ".env.public.local"
        overlayEnvFile = ""
        explicit = $false
    }
}

function Set-ProcessEnvValue([string]$Name, [string]$Value) {
    [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
}

function Apply-EnvMapToProcess($Map) {
    foreach ($entry in $Map.GetEnumerator()) {
        Set-ProcessEnvValue -Name ([string]$entry.Key) -Value ([string]$entry.Value)
    }
}

function Assert-PublicRuntimeAvailable {
    if (-not (Test-CommandExists "java")) {
        throw "Khong tim thay Java. Chay scripts/dev-env-setup.ps1 hoac cai Java 17+ roi thu lai."
    }
    if (-not (Test-BuildToolAvailable)) {
        throw "Khong tim thay Maven/Gradle wrapper hay build tool he thong. Can mvnw/gradlew, mvn hoac gradle."
    }
    if ([string]::IsNullOrWhiteSpace((Get-CloudflaredCommandPath)) -and -not (Test-FallbackTunnelAvailable)) {
        throw "Khong tim thay public tunnel tool. Can cloudflared hoac npx de mo public session."
    }
}

function Apply-DbSelection($DbInfo) {
    if (-not [string]::IsNullOrWhiteSpace([string]$DbInfo.overlayEnvFile)) {
        Apply-EnvMapToProcess (Read-EnvFileMap ([string]$DbInfo.overlayEnvFile))
    }

    Set-ProcessEnvValue -Name "APP_DATASOURCE_KIND" -Value $DbInfo.kind
    Set-ProcessEnvValue -Name "APP_DATASOURCE_H2_FILE" -Value ".data/game-public"

    if ($DbInfo.kind -eq "h2") {
        Set-ProcessEnvValue -Name "APP_DATASOURCE_ALLOW_H2_FALLBACK" -Value "true"
    } else {
        Set-ProcessEnvValue -Name "APP_DATASOURCE_ALLOW_H2_FALLBACK" -Value "true"
    }
}

function Run-Bootstrap([string]$ResolvedDbKind, [switch]$Force) {
    if ($SkipBootstrap) {
        return
    }

    $args = @{
        Mode = "public"
        Db = $ResolvedDbKind
    }
    if ($Force) {
        $args.Force = $true
    }

    & $bootstrapScript @args
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

function Require-Script([string]$Path) {
    if (-not (Test-Path $Path)) {
        throw "Khong tim thay script: $Path"
    }
}

Require-Script $bootstrapScript
Require-Script $publicStartScript

$dbInfo = Resolve-DbSelection

Write-Output "LAUNCH_MODE=public"
Write-Output "LAUNCH_MODE_REASON=Chi giu Start (Default Public) lam cach chay mac dinh"
Write-Output "RESOLVED_DB=$($dbInfo.kind)"
Write-Output "RESOLVED_DB_REASON=$($dbInfo.reason)"
Write-Output "APP_ENV_FILE=$($dbInfo.envFile)"
Write-Output "TUNNEL_MODE_REQUESTED=auto"

Run-Bootstrap -ResolvedDbKind $dbInfo.kind -Force:$ForceBootstrap
Assert-PublicRuntimeAvailable
Apply-DbSelection -DbInfo $dbInfo

& $publicStartScript -AutoBuild -Port 8080 -AppEnvFile $dbInfo.envFile -TunnelMode auto -SkipBootstrap -WaitSeconds 30
exit $LASTEXITCODE
