param(
    [string]$Profile = "prod",
    [int]$Port = 8080,
    [string]$JarPath = "",
    [string]$EnvFile = ".env.public.local",
    [switch]$AutoBuild,
    [switch]$EnableH2Fallback,
    [int]$WaitSeconds = 20
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $repoRoot "app-prod.pid"
$outLog = Join-Path $repoRoot "run-prod-public.out.log"
$errLog = Join-Path $repoRoot "run-prod-public.err.log"
$buildScript = Join-Path $PSScriptRoot "build-prod.ps1"

function Load-EnvFile([string]$Path) {
    if (-not (Test-Path $Path)) {
        return
    }
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

function Ensure-Java {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        throw "Khong tim thay lenh 'java'. Hay cai JDK/JRE va them vao PATH."
    }
}

function Set-EnvDefaultIfMissing([string]$Name, [string]$Value) {
    $current = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($current)) {
        [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    }
}

function Find-AppJar {
    $candidates = @()
    $targetDir = Join-Path $repoRoot "target"
    if (Test-Path $targetDir) {
        $candidates += Get-ChildItem $targetDir -File -Filter "*.jar" |
            Where-Object { $_.Name -notlike "*original*" }
    }
    $gradleLibDir = Join-Path $repoRoot "build\\libs"
    if (Test-Path $gradleLibDir) {
        $candidates += Get-ChildItem $gradleLibDir -File -Filter "*.jar" |
            Where-Object { $_.Name -notlike "*-plain.jar" }
    }
    return ($candidates |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1)
}

function Build-AppJar {
    if (-not (Test-Path $buildScript)) {
        throw "Khong tim thay script build: $buildScript"
    }
    & powershell -NoProfile -ExecutionPolicy Bypass -File $buildScript
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

function Stop-PreviousApp {
    if (-not (Test-Path $pidFile)) {
        return
    }
    $existingPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $existingPid) {
        return
    }
    try {
        Stop-Process -Id ([int]$existingPid) -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
    } catch {
    }
}

function Wait-AppReady([int]$TimeoutSeconds, [int]$AppPort) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $url = "http://127.0.0.1:$AppPort/Game/api/connectivity/ping"
    while ((Get-Date) -lt $deadline) {
        try {
            $res = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($res.StatusCode -eq 200) {
                return $true
            }
        } catch {
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

function Get-ListeningPid([int]$AppPort) {
    $conn = Get-NetTCPConnection -LocalPort $AppPort -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $conn) {
        return $null
    }
    return [int]$conn.OwningProcess
}

Push-Location $repoRoot
try {
    Load-EnvFile -Path (Join-Path $repoRoot $EnvFile)
    Ensure-Java

    if ($EnableH2Fallback) {
        Set-EnvDefaultIfMissing -Name "APP_DATASOURCE_ALLOW_H2_FALLBACK" -Value "true"
        Set-EnvDefaultIfMissing -Name "APP_DATASOURCE_H2_FILE" -Value ".data/game-public"
        Set-EnvDefaultIfMissing -Name "APP_EMAIL_MODE" -Value "log"
    }

    if ($AutoBuild) {
        # Stop running app before clean/package so target JAR is not locked.
        Stop-PreviousApp
        Build-AppJar
    }

    $jarFile = $null
    if ([string]::IsNullOrWhiteSpace($JarPath)) {
        $jarFile = Find-AppJar
    } else {
        if (-not (Test-Path $JarPath)) {
            throw "Khong tim thay JAR: $JarPath"
        }
        $jarFile = Get-Item $JarPath
    }

    if (-not $jarFile) {
        Write-Warning "Khong tim thay JAR. Dang auto-build 1 lan..."
        Stop-PreviousApp
        Build-AppJar
        $jarFile = Find-AppJar
    }

    if (-not $jarFile) {
        throw "Khong tim thay JAR sau khi build. Hay kiem tra scripts\\build-prod.ps1."
    }

    Stop-PreviousApp

    $javaArgs = @(
        "-jar", $jarFile.FullName,
        "--spring.profiles.active=$Profile",
        "--server.port=$Port"
    )

    $proc = Start-Process -FilePath "java" -ArgumentList $javaArgs -PassThru `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog

    $ready = Wait-AppReady -TimeoutSeconds $WaitSeconds -AppPort $Port
    $listeningPid = Get-ListeningPid -AppPort $Port
    $effectivePid = if ($listeningPid) { $listeningPid } else { $proc.Id }
    $effectivePid | Set-Content $pidFile

    Write-Output "APP_STARTER_PID=$($proc.Id)"
    Write-Output "APP_PID=$effectivePid"
    Write-Output "PROFILE=$Profile"
    Write-Output "LOCAL_URL=http://127.0.0.1:$Port/Game"
    Write-Output "LOG_OUT=$outLog"
    Write-Output "LOG_ERR=$errLog"
    if ($ready) {
        Write-Output "APP_READY=1"
    } else {
        Write-Output "APP_READY=0"
    }
} finally {
    Pop-Location
}
