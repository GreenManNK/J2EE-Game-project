param(
    [switch]$SkipStatus
)

$ErrorActionPreference = "Stop"
$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$statusScript = Join-Path $PSScriptRoot "print-runtime-status.ps1"
$dockerHelper = Join-Path $PSScriptRoot "docker-cli-helper.ps1"

if (Test-Path $dockerHelper) {
    . $dockerHelper
}

Push-Location $repoRoot
try {
    $code = Invoke-DockerCompose @("down")
    if ((Test-Path $statusScript) -and (-not $SkipStatus.IsPresent)) {
        Write-Host ""
        & $statusScript -Title "STATUS SAU KHI DUNG DOCKER" | Out-Host
    }
    exit $code
} finally {
    Pop-Location
}
