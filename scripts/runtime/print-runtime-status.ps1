param(
    [int]$Port = 8080,
    [string]$Title = "STATUS",
    [switch]$NoHints
)

$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
$dockerHelper = Join-Path $PSScriptRoot "docker-cli-helper.ps1"
$appPidFile = Join-Path $repoRoot "app-prod.pid"
$quickPidFile = Join-Path $repoRoot "cloudflared.pid"
$namedPidFile = Join-Path $repoRoot "cloudflared-named.pid"
$fallbackPidFile = Join-Path $repoRoot "public-fallback-tunnel.pid"
$quickErrLog = Join-Path $repoRoot "cloudflared.err.log"
$namedErrLog = Join-Path $repoRoot "cloudflared-named.err.log"
$fallbackOutLog = Join-Path $repoRoot "public-fallback-tunnel.out.log"
$fallbackErrLog = Join-Path $repoRoot "public-fallback-tunnel.err.log"
$appOutLog = Join-Path $repoRoot "run-prod-public.out.log"
$appErrLog = Join-Path $repoRoot "run-prod-public.err.log"
$publicUrlFile = Join-Path $repoRoot "public-game-url.txt"
$dockerContainerName = "game-hub"

if (Test-Path $dockerHelper) {
    . $dockerHelper
}

function Read-FirstLine([string]$Path) {
    if (-not (Test-Path $Path)) {
        return ""
    }
    return [string](Get-Content $Path -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Get-ProcessByPidText([string]$PidText) {
    if ([string]::IsNullOrWhiteSpace($PidText)) {
        return $null
    }
    $pidValue = 0
    if (-not [int]::TryParse($PidText.Trim(), [ref]$pidValue)) {
        return $null
    }
    try {
        return Get-Process -Id $pidValue -ErrorAction Stop
    } catch {
        return $null
    }
}

function Read-KeyValueFile([string]$Path) {
    $data = @{}
    if (-not (Test-Path $Path)) {
        return $data
    }
    foreach ($line in Get-Content $Path -ErrorAction SilentlyContinue) {
        $text = [string]$line
        if ([string]::IsNullOrWhiteSpace($text)) { continue }
        if ($text.StartsWith("#")) { continue }
        $index = $text.IndexOf("=")
        if ($index -lt 0) { continue }
        $key = $text.Substring(0, $index).Trim()
        $value = $text.Substring($index + 1).Trim()
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            $data[$key] = $value
        }
    }
    return $data
}

function Get-QuickTunnelUrl([string]$LogPath) {
    if (-not (Test-Path $LogPath)) {
        return ""
    }
    $match = Select-String -Path $LogPath -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches -ErrorAction SilentlyContinue |
        Select-Object -Last 1
    if (-not $match -or $match.Matches.Count -eq 0) {
        return ""
    }
    return $match.Matches[$match.Matches.Count - 1].Value.TrimEnd("/") + "/Game/"
}

function Get-DockerContainerStatus([string]$ContainerName) {
    $result = @{
        available = $false
        exists = $false
        running = $false
        status = ""
        ports = ""
    }
    $docker = Get-DockerCliCommand
    if (-not $docker) {
        return $result
    }
    $result.available = $true
    try {
        $format = "{{.Names}}|{{.Status}}|{{.Ports}}"
        $lines = & $docker ps -a --filter "name=$ContainerName" --format $format 2>$null
        if ($LASTEXITCODE -ne 0) {
            return $result
        }
        $line = @($lines | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } | Select-Object -First 1)[0]
        if ([string]::IsNullOrWhiteSpace([string]$line)) {
            return $result
        }
        $parts = ([string]$line).Split("|", 3)
        $result.exists = $true
        if ($parts.Length -ge 2) {
            $result.status = [string]$parts[1]
            if ($result.status.StartsWith("Up")) {
                $result.running = $true
            }
        }
        if ($parts.Length -ge 3) {
            $result.ports = [string]$parts[2]
        }
        return $result
    } catch {
        return $result
    }
}

function Test-HostResolvableOnPublicDns([string]$HostName) {
    if ([string]::IsNullOrWhiteSpace($HostName)) {
        return $false
    }
    $dnsServers = @("1.1.1.1", "8.8.8.8")
    foreach ($server in $dnsServers) {
        try {
            $answer = Resolve-DnsName -Name $HostName -Server $server -ErrorAction Stop | Select-Object -First 1
            if ($answer) {
                return $true
            }
        } catch {
        }
    }
    return $false
}

function Test-HostResolvableLocally([string]$HostName) {
    if ([string]::IsNullOrWhiteSpace($HostName)) {
        return $false
    }
    try {
        $addresses = [System.Net.Dns]::GetHostAddresses($HostName)
        return ($addresses.Count -gt 0)
    } catch {
        return $false
    }
}

function Test-HttpOk([string]$Url, [int]$TimeoutSec = 10) {
    try {
        $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        return $res.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Test-HttpBodyContains([string]$Url, [string]$ExpectedText, [int]$TimeoutSec = 10) {
    try {
        $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec -Headers @{
            "Accept" = "text/html,application/xhtml+xml"
            "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        }
        if ($res.StatusCode -ne 200) {
            return $false
        }
        if ([string]::IsNullOrWhiteSpace($ExpectedText)) {
            return $true
        }
        return ([string]$res.Content).Contains($ExpectedText)
    } catch {
        return $false
    }
}

$appPidRaw = Read-FirstLine $appPidFile
$quickPidRaw = Read-FirstLine $quickPidFile
$namedPidRaw = Read-FirstLine $namedPidFile
$fallbackPidRaw = Read-FirstLine $fallbackPidFile

$appProc = Get-ProcessByPidText $appPidRaw
$quickProc = Get-ProcessByPidText $quickPidRaw
$namedProc = Get-ProcessByPidText $namedPidRaw
$fallbackProc = Get-ProcessByPidText $fallbackPidRaw

$listen = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
$saved = Read-KeyValueFile $publicUrlFile
$savedPublicUrl = [string]$saved["PUBLIC_GAME_URL"]
$savedPublicBase = [string]$saved["PUBLIC_BASE_URL"]
$savedTunnelMode = [string]$saved["TUNNEL_MODE"]
$dockerStatus = Get-DockerContainerStatus $dockerContainerName

$quickUrl = ""
if ($quickProc) {
    $quickUrl = Get-QuickTunnelUrl $quickErrLog
    if ([string]::IsNullOrWhiteSpace($quickUrl) -and $savedTunnelMode -eq "quick" -and -not [string]::IsNullOrWhiteSpace($savedPublicUrl)) {
        $quickUrl = $savedPublicUrl.TrimEnd("/") + "/"
    }
}

$namedUrl = ""
if ($namedProc) {
    if (-not [string]::IsNullOrWhiteSpace($savedPublicUrl)) {
        $namedUrl = $savedPublicUrl.TrimEnd("/") + "/"
    } elseif (-not [string]::IsNullOrWhiteSpace($savedPublicBase)) {
        $namedUrl = $savedPublicBase.TrimEnd("/") + "/Game/"
    }
}

$fallbackUrl = ""
if ($fallbackProc -and -not [string]::IsNullOrWhiteSpace($savedPublicUrl)) {
    $fallbackUrl = $savedPublicUrl.TrimEnd("/") + "/"
}

$activeMode = ""
if ($savedTunnelMode -eq "named" -and $namedProc) {
    $activeMode = "named"
} elseif ($savedTunnelMode -eq "quick" -and $quickProc) {
    $activeMode = "quick"
} elseif (($savedTunnelMode -eq "runlocal" -or $savedTunnelMode -eq "localtunnel") -and $fallbackProc) {
    $activeMode = $savedTunnelMode
} elseif ($namedProc) {
    $activeMode = "named"
} elseif ($quickProc) {
    $activeMode = "quick"
} elseif ($fallbackProc -and -not [string]::IsNullOrWhiteSpace($savedTunnelMode)) {
    $activeMode = $savedTunnelMode
}

$activePublicUrl = ""
if ($activeMode -eq "named") {
    $activePublicUrl = $namedUrl
} elseif ($activeMode -eq "quick") {
    $activePublicUrl = $quickUrl
} elseif ($activeMode -eq "runlocal" -or $activeMode -eq "localtunnel") {
    $activePublicUrl = $fallbackUrl
}

$statusPublicUrl = if (-not [string]::IsNullOrWhiteSpace($activePublicUrl)) { $activePublicUrl } else { $savedPublicUrl }
$statusPublicHost = ""
$statusPublicDnsLocalOk = ""
$statusPublicDnsPublicOk = ""
$activePublicPingOk = ""
$activePublicWsInfoOk = ""
$activePublicPageOk = ""
if (-not [string]::IsNullOrWhiteSpace($statusPublicUrl)) {
    try {
        $statusUri = [Uri]$statusPublicUrl
        $statusPublicHost = [string]$statusUri.Host
        $statusPublicDnsLocalOk = $(if (Test-HostResolvableLocally -HostName $statusPublicHost) { "1" } else { "0" })
        $statusPublicDnsPublicOk = $(if (Test-HostResolvableOnPublicDns -HostName $statusPublicHost) { "1" } else { "0" })
    } catch {
    }
}

if (-not [string]::IsNullOrWhiteSpace($activePublicUrl)) {
    $activePublicPingOk = $(if (Test-HttpOk ($activePublicUrl.TrimEnd("/") + "/api/connectivity/ping")) { "1" } else { "0" })
    $activePublicWsInfoOk = $(if (Test-HttpOk ($activePublicUrl.TrimEnd("/") + "/ws/info?t=1")) { "1" } else { "0" })
    $activePublicPageOk = $(if (Test-HttpBodyContains -Url $activePublicUrl -ExpectedText 'name="app-context-path"') { "1" } else { "0" })
}

$status = [ordered]@{
    APP_PID_FILE = $appPidRaw
    APP_PROCESS_ALIVE = $(if ($appProc) { "1" } else { "0" })
    APP_LISTEN_8080 = $(if ($listen) { "1" } else { "0" })
    APP_LISTEN_PID = $(if ($listen) { [string]$listen.OwningProcess } else { "" })
    QUICK_TUNNEL_PID_FILE = $quickPidRaw
    QUICK_TUNNEL_PROCESS_ALIVE = $(if ($quickProc) { "1" } else { "0" })
    QUICK_TUNNEL_URL = $quickUrl
    NAMED_TUNNEL_PID_FILE = $namedPidRaw
    NAMED_TUNNEL_PROCESS_ALIVE = $(if ($namedProc) { "1" } else { "0" })
    NAMED_TUNNEL_URL = $namedUrl
    FALLBACK_TUNNEL_PID_FILE = $fallbackPidRaw
    FALLBACK_TUNNEL_PROCESS_ALIVE = $(if ($fallbackProc) { "1" } else { "0" })
    FALLBACK_TUNNEL_URL = $fallbackUrl
    ACTIVE_TUNNEL_MODE = $activeMode
    ACTIVE_PUBLIC_GAME_URL = $activePublicUrl
    ACTIVE_PUBLIC_PING_OK = $activePublicPingOk
    ACTIVE_PUBLIC_WS_INFO_OK = $activePublicWsInfoOk
    ACTIVE_PUBLIC_PAGE_OK = $activePublicPageOk
    STALE_PUBLIC_GAME_URL = $(if ([string]::IsNullOrWhiteSpace($activeMode) -and -not [string]::IsNullOrWhiteSpace($savedPublicUrl)) { $savedPublicUrl } else { "" })
    STATUS_PUBLIC_HOST = $statusPublicHost
    STATUS_PUBLIC_DNS_LOCAL_OK = $statusPublicDnsLocalOk
    STATUS_PUBLIC_DNS_PUBLIC_OK = $statusPublicDnsPublicOk
    LAST_TUNNEL_MODE = $savedTunnelMode
    LAST_PUBLIC_BASE_URL = $savedPublicBase
    LAST_PUBLIC_GAME_URL = $savedPublicUrl
    APP_LOG_OUT = $appOutLog
    APP_LOG_ERR = $appErrLog
    DOCKER_AVAILABLE = $(if ($dockerStatus.available) { "1" } else { "0" })
    DOCKER_CONTAINER_NAME = $dockerContainerName
    DOCKER_CONTAINER_EXISTS = $(if ($dockerStatus.exists) { "1" } else { "0" })
    DOCKER_CONTAINER_RUNNING = $(if ($dockerStatus.running) { "1" } else { "0" })
    DOCKER_CONTAINER_STATUS = [string]$dockerStatus.status
    DOCKER_CONTAINER_PORTS = [string]$dockerStatus.ports
    DOCKER_LOCAL_URL = "http://127.0.0.1:8080/Game"
    QUICK_TUNNEL_LOG_ERR = $quickErrLog
    NAMED_TUNNEL_LOG_ERR = $namedErrLog
    FALLBACK_TUNNEL_LOG_OUT = $fallbackOutLog
    FALLBACK_TUNNEL_LOG_ERR = $fallbackErrLog
    PUBLIC_URL_FILE = $publicUrlFile
}

Write-Output ("===== {0} =====" -f $Title)
foreach ($entry in $status.GetEnumerator()) {
    Write-Output ("{0}={1}" -f $entry.Key, [string]$entry.Value)
}
Write-Output "=================="

if (-not $NoHints) {
    Write-Output ""
    Write-Output "Neu ACTIVE_PUBLIC_GAME_URL co gia tri, gui link do cho nguoi choi."
    Write-Output "ACTIVE_TUNNEL_MODE cho biet dang dung named, quick, runlocal hoac localtunnel."
    Write-Output "STALE_PUBLIC_GAME_URL la link cu duoc luu khi hien tai khong co tunnel active."
    Write-Output "LAST_PUBLIC_GAME_URL la link lan chay gan nhat da luu (co the da het hieu luc neu tunnel da tat)."
}
