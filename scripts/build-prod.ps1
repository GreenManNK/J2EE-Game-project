param(
    [switch]$SkipTests = $true,
    [switch]$StopRunningApp = $true
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$appPidFile = Join-Path $repoRoot "app-prod.pid"

function Resolve-BuildCommand {
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

function Find-BuiltJar {
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
    return $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

function Stop-RunningAppFromPidFile {
    if (-not (Test-Path $appPidFile)) {
        return
    }
    $rawPid = Get-Content $appPidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $rawPid) {
        return
    }

    $pidValue = 0
    if (-not [int]::TryParse([string]$rawPid, [ref]$pidValue)) {
        return
    }

    try {
        $proc = Get-Process -Id $pidValue -ErrorAction Stop
    } catch {
        return
    }

    try {
        $procInfo = Get-CimInstance Win32_Process -Filter ("ProcessId = {0}" -f $pidValue) -ErrorAction SilentlyContinue
        $cmd = [string]($procInfo.CommandLine)
    } catch {
        $cmd = ""
    }

    $repoNorm = $repoRoot.Replace("/", "\").ToLowerInvariant()
    $cmdNorm = $cmd.Replace("/", "\").ToLowerInvariant()
    $safeToStop = $proc.ProcessName -match '^java' -and ($cmdNorm.Contains($repoNorm) -or $cmdNorm.Contains("game-1.0.0.jar"))
    if (-not $safeToStop) {
        Write-Warning ("PID {0} trong app-prod.pid khong ro thuoc project nay; bo qua stop de tranh kill nham." -f $pidValue)
        return
    }

    Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

Push-Location $repoRoot
try {
    if ($StopRunningApp) {
        Stop-RunningAppFromPidFile
    }

    $build = Resolve-BuildCommand
    if ($build.Kind -eq "maven") {
        if ($SkipTests) {
            & $build.Command "-DskipTests" clean package
        } else {
            & $build.Command clean package
        }
    } else {
        $args = @("--no-daemon", "clean", "bootJar")
        if ($SkipTests) {
            $args = @("--no-daemon", "clean", "bootJar", "-x", "test")
        }
        & $build.Command @args
    }
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $jar = Find-BuiltJar

    if (-not $jar) {
        throw "Khong tim thay file JAR trong target\\ hoac build\\libs\\"
    }

    Write-Output "BUILD_OK=1"
    Write-Output "BUILD_TOOL=$($build.Kind)"
    Write-Output "JAR_PATH=$($jar.FullName)"
} finally {
    Pop-Location
}
