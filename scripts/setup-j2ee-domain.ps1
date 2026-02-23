param(
    [string]$HostName = "J2EE",
    [string]$ContextPath = "/Game",
    [int]$AppPort = 8080,
    [switch]$SkipApacheRestart
)

$ErrorActionPreference = "Stop"

function Test-IsAdmin {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Ensure-Elevated {
    if (Test-IsAdmin) {
        return
    }
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) {
        throw "Khong xac dinh duoc duong dan script de tu nang quyen."
    }
    $argList = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", ('"{0}"' -f $scriptPath),
        "-HostName", ('"{0}"' -f $HostName),
        "-ContextPath", ('"{0}"' -f $ContextPath),
        "-AppPort", $AppPort
    )
    if ($SkipApacheRestart) {
        $argList += "-SkipApacheRestart"
    }
    Write-Host "Dang yeu cau quyen Administrator (UAC)..." -ForegroundColor Yellow
    Start-Process -FilePath "powershell.exe" -Verb RunAs -ArgumentList $argList -Wait | Out-Null
    exit $LASTEXITCODE
}

function Normalize-ContextPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return "/Game" }
    $v = $Path.Trim()
    if (-not $v.StartsWith("/")) { $v = "/$v" }
    while ($v.Length -gt 1 -and $v.EndsWith("/")) { $v = $v.Substring(0, $v.Length - 1) }
    return $v
}

function Get-LaragonApacheRoot {
    $base = "C:\laragon\bin\apache"
    if (-not (Test-Path $base)) {
        throw "Khong tim thay Laragon Apache tai $base"
    }
    $candidates = Get-ChildItem $base -Directory -Filter "httpd-*" | Sort-Object Name -Descending
    if (-not $candidates) {
        throw "Khong tim thay thu muc Apache trong $base"
    }
    return $candidates[0].FullName
}

function Enable-ApacheProxyModules([string]$HttpdConfPath) {
    $content = Get-Content $HttpdConfPath -Raw
    $content = $content -replace '(?m)^\s*#\s*(LoadModule\s+proxy_module\s+modules/mod_proxy\.so)\s*$', '$1'
    $content = $content -replace '(?m)^\s*#\s*(LoadModule\s+proxy_http_module\s+modules/mod_proxy_http\.so)\s*$', '$1'
    $content = $content -replace '(?m)^\s*#\s*(LoadModule\s+proxy_wstunnel_module\s+modules/mod_proxy_wstunnel\.so)\s*$', '$1'
    Set-Content -Path $HttpdConfPath -Value $content -Encoding UTF8
}

function Ensure-HostsEntry([string]$Name) {
    $hostsPath = Join-Path $env:WINDIR "System32\drivers\etc\hosts"
    $hostsRaw = Get-Content $hostsPath -Raw
    if ($hostsRaw -match ("(?im)^\s*127\.0\.0\.1\s+{0}(\s|$)" -f [regex]::Escape($Name))) {
        return "hosts: already present"
    }
    Add-Content -Path $hostsPath -Value "`r`n127.0.0.1`t$Name"
    try {
        ipconfig /flushdns | Out-Null
    } catch {
    }
    return "hosts: added 127.0.0.1 $Name"
}

function Write-VhostFile([string]$SitesEnabledDir, [string]$Name, [string]$Path, [int]$Port) {
    if (-not (Test-Path $SitesEnabledDir)) {
        New-Item -ItemType Directory -Path $SitesEnabledDir -Force | Out-Null
    }
    $vhostPath = Join-Path $SitesEnabledDir "10-j2ee-game.conf"
    $proxyTarget = "http://127.0.0.1:$Port$Path"
    $proxyWsTarget = "ws://127.0.0.1:$Port"
    $conf = @"
<VirtualHost *:80>
    ServerName $Name
    ServerAlias $($Name.ToLowerInvariant())

    ProxyRequests Off
    ProxyPreserveHost On
    AllowEncodedSlashes NoDecode

    RewriteEngine On
    RewriteCond %{HTTP:Upgrade} websocket [NC]
    RewriteCond %{REQUEST_URI} ^$Path/ws/ [NC]
    RewriteRule ^$Path/(.*)$ $proxyWsTarget$Path/`$1 [P,L]

    ProxyPass $Path $proxyTarget nocanon
    ProxyPassReverse $Path $proxyTarget

    RedirectMatch 302 ^/$ $Path/

    ErrorLog "logs/j2ee-game-error.log"
    CustomLog "logs/j2ee-game-access.log" combined
</VirtualHost>
"@
    Set-Content -Path $vhostPath -Value $conf -Encoding UTF8
    return $vhostPath
}

function Restart-Apache([string]$ApacheRoot, [switch]$SkipRestart) {
    if ($SkipRestart) {
        return "apache: restart skipped"
    }
    $httpdExe = Join-Path $ApacheRoot "bin\httpd.exe"
    Get-Process -Name httpd -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 1
    Start-Process -FilePath $httpdExe -ArgumentList @("-d", ($ApacheRoot -replace '\\','/')) | Out-Null
    Start-Sleep -Seconds 2
    $listening = Get-NetTCPConnection -State Listen -LocalPort 80 -ErrorAction SilentlyContinue
    if (-not $listening) {
        throw "Apache khong mo duoc cong 80 sau khi restart."
    }
    return "apache: restarted"
}

Ensure-Elevated

$ContextPath = Normalize-ContextPath $ContextPath
$apacheRoot = Get-LaragonApacheRoot
$httpdConf = Join-Path $apacheRoot "conf\httpd.conf"
$sitesEnabled = "C:\laragon\etc\apache2\sites-enabled"
$httpdExe = Join-Path $apacheRoot "bin\httpd.exe"

Enable-ApacheProxyModules -HttpdConfPath $httpdConf
$vhostFile = Write-VhostFile -SitesEnabledDir $sitesEnabled -Name $HostName -Path $ContextPath -Port $AppPort
$hostsResult = Ensure-HostsEntry -Name $HostName

& $httpdExe -t | Out-Null
$apacheResult = Restart-Apache -ApacheRoot $apacheRoot -SkipRestart:$SkipApacheRestart

Write-Host "OK - Cau hinh ten mien noi bo da san sang." -ForegroundColor Green
Write-Host "Host URL: http://$HostName$ContextPath/" -ForegroundColor Green
Write-Host "Apache root: $apacheRoot"
Write-Host "Vhost file: $vhostFile"
Write-Host $hostsResult
Write-Host $apacheResult
