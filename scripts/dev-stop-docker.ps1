$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$statusScript = Join-Path $PSScriptRoot "print-runtime-status.ps1"

function Test-ComposeV2 {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        return $false
    }
    try {
        & docker compose version | Out-Null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    }
}

function Invoke-Compose([string[]]$Args) {
    if (Test-ComposeV2) {
        & docker compose @Args
        return $LASTEXITCODE
    }

    $legacy = Get-Command docker-compose -ErrorAction SilentlyContinue
    if ($legacy) {
        & $legacy.Source @Args
        return $LASTEXITCODE
    }

    throw "Khong tim thay Docker Compose (docker compose hoac docker-compose)."
}

Push-Location $repoRoot
try {
    $code = Invoke-Compose @("down")
    if (Test-Path $statusScript) {
        Write-Host ""
        & $statusScript -Title "STATUS SAU KHI DUNG DOCKER" | Out-Host
    }
    exit $code
} finally {
    Pop-Location
}
