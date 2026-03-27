param(
    [switch]$Foreground
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
    $pullCode = Invoke-DockerCompose @("pull")
    if ($pullCode -ne 0) {
        Write-Warning "Khong pull duoc image Docker moi tu registry. Se thu chay bang image local neu da ton tai."
    }

    if ($Foreground) {
        $code = Invoke-DockerCompose @("up")
    } else {
        $code = Invoke-DockerCompose @("up", "-d")
        if ($code -eq 0) {
            Write-Host "Docker app da chay: http://127.0.0.1:8080/Game" -ForegroundColor Green
            Write-Host "Dung app: .\scripts\manual-start.cmd stop" -ForegroundColor Gray
            if (Test-Path $statusScript) {
                Write-Host ""
                & $statusScript -Title "DOCKER STATUS" -NoHints | Out-Host
            }
        }
    }
    exit $code
} finally {
    Pop-Location
}
