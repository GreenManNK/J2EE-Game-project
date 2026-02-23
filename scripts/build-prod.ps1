param(
    [switch]$SkipTests = $true
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repoRoot
try {
    if ($SkipTests) {
        & mvn "-DskipTests" clean package
    } else {
        & mvn clean package
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
