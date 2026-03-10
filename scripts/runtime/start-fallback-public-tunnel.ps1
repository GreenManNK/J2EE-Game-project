param(
    [int]$LocalPort = 8080,
    [string]$ContextPath = "/Game",
    [ValidateSet("auto", "runlocal", "localtunnel")]
    [string]$Provider = "auto",
    [int]$WaitSeconds = 45
)

$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$pidFile = Join-Path $repoRoot "public-fallback-tunnel.pid"
$outLog = Join-Path $repoRoot "public-fallback-tunnel.out.log"
$errLog = Join-Path $repoRoot "public-fallback-tunnel.err.log"

function Normalize-ContextPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        $v = "/Game"
    } else {
        $v = $Path.Trim()
    }
    if (-not $v.StartsWith("/")) { $v = "/" + $v }
    while ($v.Length -gt 1 -and $v.EndsWith("/")) { $v = $v.Substring(0, $v.Length - 1) }
    return $v
}

function Normalize-GameUrl([string]$Url, [string]$NormalizedContextPath) {
    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $null
    }
    $trimmed = $Url.Trim().TrimEnd("/")
    if ($trimmed.ToLowerInvariant().EndsWith($NormalizedContextPath.ToLowerInvariant())) {
        return $trimmed
    }
    return $trimmed + $NormalizedContextPath
}

function Ensure-Npx {
    $npx = Get-Command npx.cmd -ErrorAction SilentlyContinue
    if (-not $npx) {
        $npx = Get-Command npx -ErrorAction SilentlyContinue
    }
    if (-not $npx) {
        throw "Khong tim thay npx. Can Node.js de fallback public tunnel."
    }
    return $npx.Source
}

function Stop-PreviousFallback {
    if (-not (Test-Path $pidFile)) {
        return
    }
    $existingPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $existingPid) {
        return
    }
    try {
        Stop-Process -Id ([int]$existingPid) -Force -ErrorAction SilentlyContinue
    } catch {
    }
    Start-Sleep -Milliseconds 800
}

function Start-ProviderProcess([string]$NpxPath, [string]$SelectedProvider, [int]$Port) {
    if (Test-Path $outLog) { Remove-Item $outLog -Force -ErrorAction SilentlyContinue }
    if (Test-Path $errLog) { Remove-Item $errLog -Force -ErrorAction SilentlyContinue }

    $args = switch ($SelectedProvider) {
        "runlocal" { @("--yes", "runlocal", "$Port") }
        "localtunnel" { @("--yes", "localtunnel", "--port", "$Port") }
        default { throw "Provider khong hop le: $SelectedProvider" }
    }

    $process = Start-Process -FilePath $NpxPath -ArgumentList $args -PassThru `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
        -WorkingDirectory $repoRoot
    $process.Id | Set-Content $pidFile
    return $process
}

function Get-ProviderUrlRegex([string]$SelectedProvider) {
    switch ($SelectedProvider) {
        "runlocal" { return 'https://[a-z0-9.-]+\.runlocal\.eu' }
        "localtunnel" { return 'https://[a-z0-9.-]+\.(?:loca\.lt|localtunnel\.me)' }
        default { throw "Provider khong hop le: $SelectedProvider" }
    }
}

function Wait-ProviderUrl([string]$SelectedProvider, [int]$TimeoutSeconds, [string]$NormalizedContextPath) {
    $regex = Get-ProviderUrlRegex $SelectedProvider
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        foreach ($logPath in @($outLog, $errLog)) {
            if (-not (Test-Path $logPath)) {
                continue
            }
            $match = Select-String -Path $logPath -Pattern $regex -AllMatches -ErrorAction SilentlyContinue | Select-Object -Last 1
            if ($match -and $match.Matches.Count -gt 0) {
                $baseUrl = $match.Matches[$match.Matches.Count - 1].Value
                return Normalize-GameUrl -Url $baseUrl -NormalizedContextPath $NormalizedContextPath
            }
        }
        Start-Sleep -Milliseconds 700
    }
    return $null
}

function Test-ProcessAlive([int]$ProcessId) {
    try {
        $null = Get-Process -Id $ProcessId -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

$ContextPath = Normalize-ContextPath $ContextPath
$npxPath = Ensure-Npx
$providers = if ($Provider -eq "auto") { @("runlocal", "localtunnel") } else { @($Provider) }

Stop-PreviousFallback

$lastError = $null
foreach ($selectedProvider in $providers) {
    try {
        Write-Output ("FALLBACK_PROVIDER_TRY={0}" -f $selectedProvider)
        $process = Start-ProviderProcess -NpxPath $npxPath -SelectedProvider $selectedProvider -Port $LocalPort
        $publicGameUrl = Wait-ProviderUrl -SelectedProvider $selectedProvider -TimeoutSeconds $WaitSeconds -NormalizedContextPath $ContextPath
        if (-not [string]::IsNullOrWhiteSpace($publicGameUrl) -and (Test-ProcessAlive -ProcessId $process.Id)) {
            Write-Output ("TUNNEL_MODE={0}" -f $selectedProvider)
            Write-Output ("TUNNEL_PID={0}" -f $process.Id)
            Write-Output ("PUBLIC_GAME_URL={0}" -f $publicGameUrl)
            $baseUrl = $publicGameUrl.Substring(0, $publicGameUrl.Length - $ContextPath.Length)
            Write-Output ("PUBLIC_BASE_URL={0}" -f $baseUrl)
            Write-Output ("LOG_OUT={0}" -f $outLog)
            Write-Output ("LOG_ERR={0}" -f $errLog)
            exit 0
        }

        $lastError = "Khong lay duoc public URL tu provider '$selectedProvider'"
        try {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        } catch {
        }
        Start-Sleep -Milliseconds 800
    } catch {
        $lastError = $_.Exception.Message
        Start-Sleep -Milliseconds 800
    }
}

if (Test-Path $pidFile) {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

throw ("Fallback public tunnel that bai. Loi cuoi: {0}" -f $lastError)
