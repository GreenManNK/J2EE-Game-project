param(
    [switch]$SkipTests = $true
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Resolve-MavenCommand {
    $mvnw = Join-Path $repoRoot "mvnw.cmd"
    if (Test-Path $mvnw) {
        return $mvnw
    }

    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) {
        return $mvn.Source
    }

    throw "Khong tim thay Maven (`mvn`/`mvnw.cmd`). Cai Maven hoac them Maven Wrapper vao project."
}

Push-Location $repoRoot
try {
    $mavenCmd = Resolve-MavenCommand
    if ($SkipTests) {
        & $mavenCmd "-DskipTests" clean package
    } else {
        & $mavenCmd clean package
    }
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $jar = Get-ChildItem (Join-Path $repoRoot "target") -File -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*original*" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $jar) {
        throw "Khong tim thay file JAR trong target\\"
    }

    Write-Output "BUILD_OK=1"
    Write-Output "JAR_PATH=$($jar.FullName)"
} finally {
    Pop-Location
}
