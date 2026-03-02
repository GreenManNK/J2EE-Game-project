param(
    [string]$Profile = "prod",
    [int]$Port = 8080,
    [switch]$SkipDoctor,
    [switch]$NoH2Fallback,
    [switch]$ForceBootstrap
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Set-EnvDefaultIfMissing([string]$Name, [string]$Value) {
    $current = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($current)) {
        [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    }
}

function Resolve-RunTool {
    $mvnwCmd = Join-Path $repoRoot "mvnw.cmd"
    $mvnwSh = Join-Path $repoRoot "mvnw"
    if (Test-Path $mvnwCmd) {
        return @{
            Kind = "maven"
            Command = $mvnwCmd
        }
    }
    if (Test-Path $mvnwSh) {
        return @{
            Kind = "maven"
            Command = $mvnwSh
        }
    }

    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) {
        return @{
            Kind = "maven"
            Command = $mvn.Source
        }
    }

    $gradlewBat = Join-Path $repoRoot "gradlew.bat"
    $gradlewSh = Join-Path $repoRoot "gradlew"
    if (Test-Path $gradlewBat) {
        return @{
            Kind = "gradle"
            Command = $gradlewBat
        }
    }
    if (Test-Path $gradlewSh) {
        return @{
            Kind = "gradle"
            Command = $gradlewSh
        }
    }

    $gradle = Get-Command gradle -ErrorAction SilentlyContinue
    if ($gradle) {
        return @{
            Kind = "gradle"
            Command = $gradle.Source
        }
    }

    throw "Khong tim thay Maven/Gradle. Hay dung mvnw/mvn hoac gradlew/gradle."
}

if (-not $SkipDoctor) {
    $bootstrap = Join-Path $PSScriptRoot "dev-env-bootstrap.ps1"
    if ($ForceBootstrap) {
        & $bootstrap -Mode local -Db auto -Force
    } else {
        & $bootstrap -Mode local -Db auto
    }
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

$dataDir = Join-Path $repoRoot ".data"
if (-not (Test-Path $dataDir)) {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
}

Set-EnvDefaultIfMissing -Name "APP_EMAIL_MODE" -Value "log"
if (-not $NoH2Fallback) {
    Set-EnvDefaultIfMissing -Name "APP_DATASOURCE_ALLOW_H2_FALLBACK" -Value "true"
    Set-EnvDefaultIfMissing -Name "APP_DATASOURCE_H2_FILE" -Value ".data/game-local"
}

Push-Location $repoRoot
try {
    $runTool = Resolve-RunTool
    Write-Host "Starting Game Hub locally at http://127.0.0.1:$Port/Game" -ForegroundColor Cyan
    Write-Host "Profile=$Profile | H2Fallback=$($NoH2Fallback -eq $false) | BuildTool=$($runTool.Kind)" -ForegroundColor Gray
    if ($runTool.Kind -eq "maven") {
        & $runTool.Command ("-Dspring-boot.run.profiles=" + $Profile) ("-Dspring-boot.run.arguments=--server.port=" + $Port) "spring-boot:run"
    } else {
        & $runTool.Command "--no-daemon" "bootRun" ("-PspringProfile=" + $Profile) ("-PserverPort=" + $Port)
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
