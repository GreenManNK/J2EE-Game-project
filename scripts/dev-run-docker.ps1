param(
    [switch]$Foreground
)

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
    if ($Foreground) {
        $code = Invoke-Compose @("up", "--build")
    } else {
        $code = Invoke-Compose @("up", "--build", "-d")
        if ($code -eq 0) {
            Write-Host "Docker app da chay: http://127.0.0.1:8080/Game" -ForegroundColor Green
            Write-Host "Dung app: powershell -File .\scripts\dev-stop-docker.ps1" -ForegroundColor Gray
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
