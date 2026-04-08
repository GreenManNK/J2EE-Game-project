param(
    [string]$Token = "",
    [string]$EnvFile = ".env.public.local",
    [switch]$NoDownload,
    [int]$WaitSeconds = 20
)

$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$toolsDir = Join-Path $repoRoot ".tools"
$cloudflaredExe = Join-Path $toolsDir "cloudflared.exe"
$pidFile = Join-Path $repoRoot "cloudflared-named.pid"
$outLog = Join-Path $repoRoot "cloudflared-named.out.log"
$errLog = Join-Path $repoRoot "cloudflared-named.err.log"

function Load-EnvFile([string]$Path) {
    if (-not (Test-Path $Path)) { return }
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

function Ensure-Cloudflared {
    if (Test-Path $cloudflaredExe) { return }
    if ($NoDownload) {
        throw "Khong tim thay cloudflared tai $cloudflaredExe"
    }
    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    Invoke-WebRequest -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" -OutFile $cloudflaredExe
}

function Stop-PreviousTunnel {
    if (-not (Test-Path $pidFile)) { return }
    $cfPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $cfPid) { return }
    try {
        Stop-Process -Id ([int]$cfPid) -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
    } catch {
    }
}

function Wait-TunnelReady([int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $errLog) {
            $ready = Select-String -Path $errLog -Pattern "Registered tunnel connection" -Quiet -ErrorAction SilentlyContinue
            if ($ready) { return $true }
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

Push-Location $repoRoot
try {
    Load-EnvFile -Path (Join-Path $repoRoot $EnvFile)
    Ensure-Cloudflared

    $effectiveToken = $Token
    if ([string]::IsNullOrWhiteSpace($effectiveToken)) {
        $effectiveToken = [Environment]::GetEnvironmentVariable("CLOUDFLARE_TUNNEL_TOKEN", "Process")
    }
    if ([string]::IsNullOrWhiteSpace($effectiveToken)) {
        throw "Thieu CLOUDFLARE_TUNNEL_TOKEN. Dat trong env hoac .env.public.local"
    }

    Stop-PreviousTunnel
    if (Test-Path $outLog) { Remove-Item $outLog -Force -ErrorAction SilentlyContinue }
    if (Test-Path $errLog) { Remove-Item $errLog -Force -ErrorAction SilentlyContinue }

    $proc = Start-Process -FilePath $cloudflaredExe `
        -ArgumentList @("tunnel", "run", "--token", $effectiveToken, "--no-autoupdate") `
        -PassThru `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog
    $proc.Id | Set-Content $pidFile

    $ready = Wait-TunnelReady -TimeoutSeconds $WaitSeconds
    $publicBaseUrl = [Environment]::GetEnvironmentVariable("PUBLIC_BASE_URL", "Process")
    $publicGameUrl = [Environment]::GetEnvironmentVariable("PUBLIC_GAME_URL", "Process")
    if ([string]::IsNullOrWhiteSpace($publicGameUrl) -and -not [string]::IsNullOrWhiteSpace($publicBaseUrl)) {
        $base = $publicBaseUrl.TrimEnd("/")
        $publicGameUrl = $base + "/Game/"
    } elseif (-not [string]::IsNullOrWhiteSpace($publicGameUrl)) {
        $publicGameUrl = $publicGameUrl.TrimEnd("/") + "/"
    }

    Write-Output "TUNNEL_PID=$($proc.Id)"
    Write-Output "TUNNEL_MODE=named"
    if ($ready) {
        Write-Output "TUNNEL_READY=1"
    } else {
        Write-Output "TUNNEL_READY=0"
    }
    if (-not [string]::IsNullOrWhiteSpace($publicBaseUrl)) {
        Write-Output "PUBLIC_BASE_URL=$publicBaseUrl"
    }
    if (-not [string]::IsNullOrWhiteSpace($publicGameUrl)) {
        Write-Output "PUBLIC_GAME_URL=$publicGameUrl"
    }
    Write-Output "LOG_OUT=$outLog"
    Write-Output "LOG_ERR=$errLog"
} finally {
    Pop-Location
}
