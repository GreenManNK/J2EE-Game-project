param(
    [string]$Url = "http://J2EE/Game/"
)

$browsers = @(
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
)

$browser = $browsers | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) {
    throw "Khong tim thay Edge/Chrome de mo URL."
}

$profileDir = Join-Path $env:TEMP "j2ee-host-override-browser"
New-Item -ItemType Directory -Force -Path $profileDir | Out-Null

$args = @(
    "--user-data-dir=$profileDir",
    "--host-resolver-rules=MAP J2EE 127.0.0.1",
    $Url
)

Start-Process -FilePath $browser -ArgumentList $args | Out-Null
Write-Output "OPENED=$Url"
Write-Output "BROWSER=$browser"
