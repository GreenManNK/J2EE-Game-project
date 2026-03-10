param(
    [ValidateSet("auto", "local", "public", "docker")]
    [string]$Mode = "auto",
    [ValidateSet("auto", "h2", "mysql", "postgres")]
    [string]$Db = "auto",
    [ValidateSet("auto", "named", "quick", "runlocal", "localtunnel")]
    [string]$TunnelMode = "auto",
    [switch]$ForceBootstrap,
    [switch]$SkipBootstrap
)

$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$dockerHelper = Join-Path $PSScriptRoot "docker-cli-helper.ps1"
$bootstrapScript = Join-Path $scriptsRoot "dev-env-bootstrap.ps1"
$localStartScript = Join-Path $PSScriptRoot "start-prod-app.ps1"
$publicStartScript = Join-Path $PSScriptRoot "start-remote-public-session.ps1"
$dockerStartScript = Join-Path $PSScriptRoot "dev-run-docker.ps1"

if (Test-Path $dockerHelper) {
    . $dockerHelper
}

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

function Test-LocalRuntimeAvailable {
    return (Test-CommandExists "java") -and (Test-BuildToolAvailable)
}

function Test-PublicRuntimeAvailable {
    return (Test-LocalRuntimeAvailable) -and (-not [string]::IsNullOrWhiteSpace((Get-CloudflaredCommandPath)))
}

function Test-DockerRuntimeAvailable {
    return (-not [string]::IsNullOrWhiteSpace((Get-DockerCliCommand)))
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

function Resolve-ModeSelection([string]$RequestedMode) {
    if ($RequestedMode -ne "auto") {
        return @{
            mode = $RequestedMode
            reason = "Dung mode duoc chi dinh thu cong"
        }
    }

    if (Test-PublicRuntimeAvailable) {
        return @{
            mode = "public"
            reason = "Phat hien Java + build tool + cloudflared, uu tien session public mac dinh"
        }
    }

    if (Test-LocalRuntimeAvailable) {
        return @{
            mode = "local"
            reason = "Phat hien Java + build tool, fallback ve local"
        }
    }

    if (Test-DockerRuntimeAvailable) {
        return @{
            mode = "docker"
            reason = "Khong du tool local/public, fallback ve Docker"
        }
    }

    throw "Khong tim thay moi truong chay phu hop. Can Java + build tool de chay local/public, hoac Docker de chay container."
}

function Resolve-DbSelection([string]$RequestedDb, [string]$SelectedMode) {
    $sharedEnv = Read-EnvFileMap ".env.public.local"
    $mysqlEnv = Read-EnvFileMap ".env.public.mysql.local"
    $postgresEnv = Read-EnvFileMap ".env.public.postgres.local"
    $processDbKind = Normalize-DbKind ([Environment]::GetEnvironmentVariable("APP_DATASOURCE_KIND", "Process"))

    if ($SelectedMode -eq "docker") {
        return @{
            kind = "h2"
            reason = "Docker mode hien tai duoc toi uu cho H2 noi bo"
            envFile = ".env.public.local"
            overlayEnvFile = ""
            explicit = $false
        }
    }

    if ($RequestedDb -ne "auto") {
        return @{
            kind = $RequestedDb
            reason = "Dung DB duoc chi dinh thu cong"
            envFile = if ($RequestedDb -eq "postgres") { ".env.public.postgres.local" } elseif ($RequestedDb -eq "mysql") { ".env.public.mysql.local" } else { ".env.public.local" }
            overlayEnvFile = ""
            explicit = $true
        }
    }

    if ($processDbKind -ne "auto") {
        return @{
            kind = $processDbKind
            reason = "Dung DB theo APP_DATASOURCE_KIND trong moi truong hien tai"
            envFile = if ($processDbKind -eq "postgres") { ".env.public.postgres.local" } elseif ($processDbKind -eq "mysql") { ".env.public.mysql.local" } else { ".env.public.local" }
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

function Apply-DbSelection($DbInfo, [string]$SelectedMode) {
    if (-not [string]::IsNullOrWhiteSpace([string]$DbInfo.overlayEnvFile)) {
        Apply-EnvMapToProcess (Read-EnvFileMap ([string]$DbInfo.overlayEnvFile))
    }

    Set-ProcessEnvValue -Name "APP_DATASOURCE_KIND" -Value $DbInfo.kind

    if ($DbInfo.kind -eq "h2") {
        Set-ProcessEnvValue -Name "APP_DATASOURCE_ALLOW_H2_FALLBACK" -Value "true"
    } elseif ($DbInfo.explicit) {
        Set-ProcessEnvValue -Name "APP_DATASOURCE_ALLOW_H2_FALLBACK" -Value "false"
    } else {
        Set-ProcessEnvValue -Name "APP_DATASOURCE_ALLOW_H2_FALLBACK" -Value "true"
    }

    if ($SelectedMode -eq "local") {
        Set-ProcessEnvValue -Name "APP_DATASOURCE_H2_FILE" -Value ".data/game-local"
    } elseif ($SelectedMode -eq "public") {
        Set-ProcessEnvValue -Name "APP_DATASOURCE_H2_FILE" -Value ".data/game-public"
    }
}

function Run-Bootstrap([string]$SelectedMode, [string]$ResolvedDbKind, [switch]$Force) {
    if ($SkipBootstrap) {
        return
    }
    if ($SelectedMode -eq "docker") {
        return
    }

    $args = @{
        Mode = $SelectedMode
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

Require-Script $localStartScript
Require-Script $publicStartScript
Require-Script $dockerStartScript
Require-Script $bootstrapScript

$modeInfo = Resolve-ModeSelection -RequestedMode $Mode
$dbInfo = Resolve-DbSelection -RequestedDb $Db -SelectedMode $modeInfo.mode

Write-Output "LAUNCH_MODE=$($modeInfo.mode)"
Write-Output "LAUNCH_MODE_REASON=$($modeInfo.reason)"
Write-Output "RESOLVED_DB=$($dbInfo.kind)"
Write-Output "RESOLVED_DB_REASON=$($dbInfo.reason)"
Write-Output "APP_ENV_FILE=$($dbInfo.envFile)"
Write-Output "TUNNEL_MODE_REQUESTED=$TunnelMode"

Run-Bootstrap -SelectedMode $modeInfo.mode -ResolvedDbKind $dbInfo.kind -Force:$ForceBootstrap
Apply-DbSelection -DbInfo $dbInfo -SelectedMode $modeInfo.mode

switch ($modeInfo.mode) {
    "local" {
        & $localStartScript -AutoBuild -Port 8080 -EnvFile $dbInfo.envFile -EnableH2Fallback -WaitSeconds 45
        exit $LASTEXITCODE
    }
    "public" {
        & $publicStartScript -AutoBuild -Port 8080 -AppEnvFile $dbInfo.envFile -TunnelMode $TunnelMode -SkipBootstrap -WaitSeconds 30
        exit $LASTEXITCODE
    }
    "docker" {
        & $dockerStartScript
        exit $LASTEXITCODE
    }
    default {
        throw "Mode khong duoc ho tro: $($modeInfo.mode)"
    }
}
