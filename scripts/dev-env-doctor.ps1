param(
    [ValidateSet("local", "public")]
    [string]$Mode = "local",
    [ValidateSet("auto", "h2", "mysql", "postgres")]
    [string]$Db = "auto",
    [switch]$InstallMissing,
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$shouldInstall = $InstallMissing.IsPresent -and (-not $CheckOnly.IsPresent)
$global:AptUpdated = $false

function Write-Title([string]$Text) { Write-Host "== $Text ==" -ForegroundColor Cyan }
function Write-Ok([string]$Text) { Write-Host "[OK] $Text" -ForegroundColor Green }
function Write-WarnLine([string]$Text) { Write-Host "[WARN] $Text" -ForegroundColor Yellow }
function Write-InfoLine([string]$Text) { Write-Host "[INFO] $Text" -ForegroundColor Gray }
function Write-ErrLine([string]$Text) { Write-Host "[ERR] $Text" -ForegroundColor Red }

function Test-CommandExists([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-LocalCloudflaredPath {
    return (Join-Path $repoRoot ".tools\cloudflared.exe")
}

function Get-LocalMavenWrapperPath {
    $cmd = Join-Path $repoRoot "mvnw.cmd"
    if (Test-Path $cmd) {
        return $cmd
    }
    $sh = Join-Path $repoRoot "mvnw"
    if (Test-Path $sh) {
        return $sh
    }
    return $cmd
}

function Get-LocalGradleWrapperPath {
    $bat = Join-Path $repoRoot "gradlew.bat"
    if (Test-Path $bat) {
        return $bat
    }
    $sh = Join-Path $repoRoot "gradlew"
    if (Test-Path $sh) {
        return $sh
    }
    return $bat
}

function Test-ToolAvailable([string]$CommandName) {
    if (Test-CommandExists $CommandName) {
        return $true
    }
    if ($CommandName -eq "mvn") {
        return (Test-Path (Get-LocalMavenWrapperPath))
    }
    if ($CommandName -eq "gradle") {
        return (Test-Path (Get-LocalGradleWrapperPath))
    }
    if ($CommandName -eq "cloudflared" -and $env:OS -eq "Windows_NT") {
        return (Test-Path (Get-LocalCloudflaredPath))
    }
    return $false
}

function Get-JavaMajorVersion {
    if (-not (Test-CommandExists "java")) { return $null }
    try {
        $raw = $null
        if ($env:OS -eq "Windows_NT" -and (Test-CommandExists "cmd")) {
            $raw = (& cmd /c "java -version 2>&1" | Out-String)
        } else {
            $raw = (& java -version 2>&1 | Out-String)
        }
        if (-not $raw) { return $null }
        $s = [string]$raw
        if ($s -match '"(?<v>\d+)(\.\d+)?') { return [int]$Matches.v }
        if ($s -match 'version\s+(?<v>\d+)') { return [int]$Matches.v }
    } catch {
    }
    return $null
}

function Get-MavenVersionText {
    $mavenCmd = $null
    if (Test-CommandExists "mvn") {
        $mavenCmd = "mvn"
    } elseif (Test-Path (Get-LocalMavenWrapperPath)) {
        $mavenCmd = (Get-LocalMavenWrapperPath)
    } else {
        return $null
    }
    try {
        $line = (& $mavenCmd -v 2>&1 | Select-Object -First 1)
        $s = [string]$line
        if ($s -match 'Apache Maven\s+(?<v>\d+(\.\d+){1,3})') {
            return $Matches.v
        }
    } catch {
    }
    return $null
}

function Get-GradleVersionText {
    $gradleCmd = $null
    if (Test-CommandExists "gradle") {
        $gradleCmd = "gradle"
    } elseif (Test-Path (Get-LocalGradleWrapperPath)) {
        $gradleCmd = (Get-LocalGradleWrapperPath)
    } else {
        return $null
    }
    try {
        $raw = (& $gradleCmd -v 2>&1 | Out-String)
        $s = [string]$raw
        if ($s -match 'Gradle\s+(?<v>\d+(\.\d+){1,3})') {
            return $Matches.v
        }
    } catch {
    }
    return $null
}

function Get-NodeVersionText {
    if (-not (Test-CommandExists "node")) { return $null }
    try {
        $v = (& node -v 2>$null)
        if ($v) { return ([string]$v).TrimStart("v") }
    } catch {
    }
    return $null
}

function Convert-ToVersion([string]$VersionText) {
    if ([string]::IsNullOrWhiteSpace($VersionText)) { return $null }
    $parts = $VersionText.Split(".")
    while ($parts.Count -lt 4) { $parts += "0" }
    $normalized = ($parts[0..3] -join ".")
    try { return [version]$normalized } catch { return $null }
}

function Test-VersionAtLeast([string]$Actual, [string]$Minimum) {
    $a = Convert-ToVersion $Actual
    $m = Convert-ToVersion $Minimum
    if (-not $a -or -not $m) { return $false }
    return ($a -ge $m)
}

function Get-PlatformName {
    if ($env:OS -eq "Windows_NT") { return "windows" }
    try {
        $uname = (& uname -s 2>$null)
        if ($uname -match "Darwin") { return "macos" }
        if ($uname) { return "linux" }
    } catch {
    }
    return "unknown"
}

function Get-PackageManager {
    $platform = Get-PlatformName
    if ($platform -eq "windows") {
        foreach ($name in @("winget", "choco", "scoop")) {
            if (Test-CommandExists $name) { return $name }
        }
        return $null
    }
    if ($platform -eq "macos") {
        if (Test-CommandExists "brew") { return "brew" }
        return $null
    }
    foreach ($name in @("apt-get", "dnf", "yum", "pacman", "zypper")) {
        if (Test-CommandExists $name) { return $name }
    }
    return $null
}

function Invoke-InstallCommand([string]$Exe, [string[]]$ArgumentList) {
    $platform = Get-PlatformName
    Write-InfoLine ("RUN: " + $Exe + " " + ($ArgumentList -join " "))
    try {
        if ($platform -eq "windows") {
            & $Exe @ArgumentList
        } else {
            $needSudo = (Test-CommandExists "sudo")
            if ($needSudo) {
                & sudo $Exe @ArgumentList
            } else {
                & $Exe @ArgumentList
            }
        }
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    }
}

function Try-InstallCloudflaredLocalWindows {
    if ($env:OS -ne "Windows_NT") { return $false }
    try {
        $toolsDir = Join-Path $repoRoot ".tools"
        $exePath = Get-LocalCloudflaredPath
        if (Test-Path $exePath) {
            return $true
        }
        New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
        $url = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
        Write-InfoLine "Dang tai cloudflared local vao .tools/cloudflared.exe ..."
        Invoke-WebRequest -Uri $url -OutFile $exePath
        return (Test-Path $exePath)
    } catch {
        return $false
    }
}

function Ensure-AptUpdated {
    if ($global:AptUpdated) { return $true }
    if (-not (Test-CommandExists "apt-get")) { return $true }
    if (Invoke-InstallCommand "apt-get" @("update")) {
        $global:AptUpdated = $true
        return $true
    }
    return $false
}

function Try-InstallTool([string]$ToolKey) {
    $pm = Get-PackageManager
    if ($ToolKey -eq "cloudflared" -and $env:OS -eq "Windows_NT") {
        if (Try-InstallCloudflaredLocalWindows) {
            return $true
        }
    }
    if (-not $pm) {
        Write-WarnLine "Khong tim thay package manager duoc ho tro de cai tu dong."
        if ($ToolKey -eq "cloudflared" -and $env:OS -eq "Windows_NT") {
            return (Try-InstallCloudflaredLocalWindows)
        }
        return $false
    }

    switch ($pm) {
        "winget" {
            $ids = switch ($ToolKey) {
                "java21" { @("EclipseAdoptium.Temurin.21.JDK", "Microsoft.OpenJDK.21") }
                "maven" { @("Apache.Maven") }
                "gradle" { @("Gradle.Gradle") }
                "git" { @("Git.Git") }
                "node" { @("OpenJS.NodeJS.LTS") }
                "cloudflared" { @("Cloudflare.cloudflared") }
                default { @() }
            }
            foreach ($id in $ids) {
                $ok = Invoke-InstallCommand "winget" @("install", "--id", $id, "-e", "--accept-package-agreements", "--accept-source-agreements", "--silent")
                if ($ok) { return $true }
            }
            if ($ToolKey -eq "cloudflared" -and $env:OS -eq "Windows_NT") {
                return (Try-InstallCloudflaredLocalWindows)
            }
            return $false
        }
        "choco" {
            $names = switch ($ToolKey) {
                "java21" { @("temurin21") }
                "maven" { @("maven") }
                "gradle" { @("gradle") }
                "git" { @("git") }
                "node" { @("nodejs-lts") }
                "cloudflared" { @("cloudflared") }
                default { @() }
            }
            foreach ($name in $names) {
                $ok = Invoke-InstallCommand "choco" @("install", $name, "-y")
                if ($ok) { return $true }
            }
            if ($ToolKey -eq "cloudflared" -and $env:OS -eq "Windows_NT") {
                return (Try-InstallCloudflaredLocalWindows)
            }
            return $false
        }
        "scoop" {
            $names = switch ($ToolKey) {
                "java21" { @("openjdk21") }
                "maven" { @("maven") }
                "gradle" { @("gradle") }
                "git" { @("git") }
                "node" { @("nodejs-lts") }
                "cloudflared" { @("cloudflared") }
                default { @() }
            }
            foreach ($name in $names) {
                $ok = Invoke-InstallCommand "scoop" @("install", $name)
                if ($ok) { return $true }
            }
            if ($ToolKey -eq "cloudflared" -and $env:OS -eq "Windows_NT") {
                return (Try-InstallCloudflaredLocalWindows)
            }
            return $false
        }
        "brew" {
            $names = switch ($ToolKey) {
                "java21" { @("openjdk@21") }
                "maven" { @("maven") }
                "gradle" { @("gradle") }
                "git" { @("git") }
                "node" { @("node") }
                "cloudflared" { @("cloudflared") }
                default { @() }
            }
            foreach ($name in $names) {
                $ok = Invoke-InstallCommand "brew" @("install", $name)
                if ($ok) { return $true }
            }
            return $false
        }
        "apt-get" {
            if (-not (Ensure-AptUpdated)) { return $false }
            $names = switch ($ToolKey) {
                "java21" { @("openjdk-21-jdk") }
                "maven" { @("maven") }
                "gradle" { @("gradle") }
                "git" { @("git") }
                "node" { @("nodejs", "npm") }
                "cloudflared" { @("cloudflared") }
                default { @() }
            }
            if ($ToolKey -eq "node") {
                $ok1 = Invoke-InstallCommand "apt-get" @("install", "-y", "nodejs")
                $ok2 = Invoke-InstallCommand "apt-get" @("install", "-y", "npm")
                return ($ok1 -and $ok2)
            }
            foreach ($name in $names) {
                $ok = Invoke-InstallCommand "apt-get" @("install", "-y", $name)
                if ($ok) { return $true }
            }
            return $false
        }
        "dnf" {
            $names = switch ($ToolKey) {
                "java21" { @("java-21-openjdk-devel") }
                "maven" { @("maven") }
                "gradle" { @("gradle") }
                "git" { @("git") }
                "node" { @("nodejs", "npm") }
                "cloudflared" { @("cloudflared") }
                default { @() }
            }
            if ($ToolKey -eq "node") {
                $ok1 = Invoke-InstallCommand "dnf" @("install", "-y", "nodejs")
                return $ok1
            }
            foreach ($name in $names) {
                $ok = Invoke-InstallCommand "dnf" @("install", "-y", $name)
                if ($ok) { return $true }
            }
            return $false
        }
        "yum" {
            $names = switch ($ToolKey) {
                "java21" { @("java-21-openjdk-devel") }
                "maven" { @("maven") }
                "gradle" { @("gradle") }
                "git" { @("git") }
                "node" { @("nodejs", "npm") }
                "cloudflared" { @("cloudflared") }
                default { @() }
            }
            foreach ($name in $names) {
                $ok = Invoke-InstallCommand "yum" @("install", "-y", $name)
                if ($ok) { return $true }
            }
            return $false
        }
        "pacman" {
            $names = switch ($ToolKey) {
                "java21" { @("jdk21-openjdk") }
                "maven" { @("maven") }
                "gradle" { @("gradle") }
                "git" { @("git") }
                "node" { @("nodejs", "npm") }
                "cloudflared" { @("cloudflared") }
                default { @() }
            }
            if ($ToolKey -eq "node") {
                $ok1 = Invoke-InstallCommand "pacman" @("-S", "--noconfirm", "nodejs")
                $ok2 = Invoke-InstallCommand "pacman" @("-S", "--noconfirm", "npm")
                return ($ok1 -and $ok2)
            }
            foreach ($name in $names) {
                $ok = Invoke-InstallCommand "pacman" @("-S", "--noconfirm", $name)
                if ($ok) { return $true }
            }
            return $false
        }
        "zypper" {
            $names = switch ($ToolKey) {
                "java21" { @("java-21-openjdk-devel") }
                "maven" { @("maven") }
                "gradle" { @("gradle") }
                "git" { @("git") }
                "node" { @("nodejs18", "nodejs") }
                "cloudflared" { @("cloudflared") }
                default { @() }
            }
            foreach ($name in $names) {
                $ok = Invoke-InstallCommand "zypper" @("--non-interactive", "install", $name)
                if ($ok) { return $true }
            }
            return $false
        }
    }
    return $false
}

function Ensure-ExampleFile([string]$TargetRelative, [string]$ExampleRelative) {
    $target = Join-Path $repoRoot $TargetRelative
    $example = Join-Path $repoRoot $ExampleRelative
    if (Test-Path $target) {
        Write-Ok "$TargetRelative ton tai"
        return
    }
    if (-not (Test-Path $example)) {
        Write-WarnLine "Khong tim thay file mau: $ExampleRelative"
        return
    }
    Copy-Item $example $target -Force
    Write-Ok "Da tao $TargetRelative tu $ExampleRelative"
}

function Ensure-Directory([string]$RelativePath) {
    $full = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path $full)) {
        New-Item -ItemType Directory -Path $full -Force | Out-Null
        Write-Ok "Da tao thu muc $RelativePath"
    } else {
        Write-Ok "Thu muc $RelativePath da san sang"
    }
}

function Ensure-Tool(
    [string]$DisplayName,
    [string]$CommandName,
    [string]$InstallerKey,
    [bool]$Required,
    [scriptblock]$VersionGetter,
    [string]$MinVersion
) {
    $versionText = $null
    $exists = Test-ToolAvailable $CommandName
    if ($exists -and $VersionGetter) {
        $versionText = & $VersionGetter
    }

    $versionOk = $true
    if ($exists -and $MinVersion) {
        if ($CommandName -eq "java") {
            $versionOk = (-not [string]::IsNullOrWhiteSpace([string]$versionText)) -and ([int]$versionText -ge [int]$MinVersion)
        } else {
            $versionOk = Test-VersionAtLeast -Actual ([string]$versionText) -Minimum $MinVersion
        }
    }

    if ($exists -and $versionOk) {
        if ($versionText) {
            Write-Ok "$DisplayName OK ($versionText)"
        } else {
            Write-Ok "$DisplayName OK"
        }
        return $true
    }

    if ($exists -and (-not $versionOk)) {
        Write-WarnLine "$DisplayName dang co nhung version chua dat (can >= $MinVersion, hien tai: $versionText)"
    } else {
        $msg = if ($Required) { "$DisplayName thieu (bat buoc)" } else { "$DisplayName thieu (khuyen nghi)" }
        Write-WarnLine $msg
    }

    if ($shouldInstall) {
        Write-InfoLine "Dang thu cai tu dong $DisplayName ..."
        $installed = Try-InstallTool $InstallerKey
        if (-not $installed) {
            Write-WarnLine "Cai tu dong $DisplayName that bai. Hay cai thu cong roi mo terminal moi."
        }
        # Kiem tra lai trong cung process (co the PATH chua cap nhat)
        $exists = Test-ToolAvailable $CommandName
        if ($exists -and $VersionGetter) { $versionText = & $VersionGetter }
        if ($exists) {
            if ($MinVersion) {
                if ($CommandName -eq "java") {
                    $versionOk = (-not [string]::IsNullOrWhiteSpace([string]$versionText)) -and ([int]$versionText -ge [int]$MinVersion)
                } else {
                    $versionOk = Test-VersionAtLeast -Actual ([string]$versionText) -Minimum $MinVersion
                }
            } else {
                $versionOk = $true
            }
        } else {
            $versionOk = $false
        }
    }

    if ($Required) {
        if (-not $exists) { return $false }
        if ($MinVersion -and (-not $versionOk)) { return $false }
    }
    return $true
}

$requiredFailures = New-Object System.Collections.Generic.List[string]

Write-Title "Game Hub Dev Environment Doctor"
Write-InfoLine "Repo: $repoRoot"
Write-InfoLine "Mode: $Mode | DB: $Db | AutoInstall: $shouldInstall"
$pm = Get-PackageManager
if ($pm) {
    Write-InfoLine "Package manager: $pm"
} else {
    Write-WarnLine "Khong phat hien package manager ho tro (chi co the check, khong cai tu dong)."
}

if (-not (Ensure-Tool -DisplayName "Java (JDK)" -CommandName "java" -InstallerKey "java21" -Required $true -VersionGetter { Get-JavaMajorVersion } -MinVersion "17")) {
    $requiredFailures.Add("Java 17+")
}
if (-not (Ensure-Tool -DisplayName "Maven (hoac Maven Wrapper)" -CommandName "mvn" -InstallerKey "maven" -Required $true -VersionGetter { Get-MavenVersionText } -MinVersion "3.8.6")) {
    $requiredFailures.Add("Maven 3.8.6+ (hoac mvnw)")
}
[void](Ensure-Tool -DisplayName "Gradle (hoac Gradle Wrapper)" -CommandName "gradle" -InstallerKey "gradle" -Required $false -VersionGetter { Get-GradleVersionText } -MinVersion "8.7.0")
[void](Ensure-Tool -DisplayName "Git" -CommandName "git" -InstallerKey "git" -Required $false -VersionGetter $null -MinVersion "")
[void](Ensure-Tool -DisplayName "Node.js" -CommandName "node" -InstallerKey "node" -Required $false -VersionGetter { Get-NodeVersionText } -MinVersion "18.0.0")

if ($Mode -eq "public") {
    if (-not (Ensure-Tool -DisplayName "cloudflared" -CommandName "cloudflared" -InstallerKey "cloudflared" -Required $true -VersionGetter $null -MinVersion "")) {
        $requiredFailures.Add("cloudflared")
    }
}

Write-Title "Project Files And Local Data"
Ensure-Directory ".data"
Ensure-ExampleFile ".env.public.local" ".env.public.example"
if ($Db -eq "mysql") {
    Ensure-ExampleFile ".env.public.mysql.local" ".env.public.mysql.example"
    if (-not (Test-CommandExists "mysql")) {
        Write-WarnLine "Khong tim thay lenh 'mysql' (chi de kiem tra/quan ly DB, app van co the ket noi bang JDBC neu DB server san sang)."
    } else {
        Write-Ok "MySQL CLI ton tai"
    }
}
if ($Db -eq "postgres") {
    Ensure-ExampleFile ".env.public.postgres.local" ".env.public.postgres.example"
    if (-not (Test-CommandExists "psql")) {
        Write-WarnLine "Khong tim thay lenh 'psql' (chi de kiem tra/quan ly DB, app van co the ket noi bang JDBC neu DB server san sang)."
    } else {
        Write-Ok "psql ton tai"
    }
}

Write-Title "Quick Start"
Write-Host "Windows (PowerShell): powershell -ExecutionPolicy Bypass -File .\scripts\dev-run-local.ps1" -ForegroundColor White
Write-Host "macOS/Linux (bash):  bash ./scripts/dev-run-local.sh" -ForegroundColor White
Write-Host "Maven wrapper: ./mvnw spring-boot:run (macOS/Linux) | .\mvnw.cmd spring-boot:run (Windows)" -ForegroundColor White
Write-Host "Gradle wrapper: ./gradlew bootRun (macOS/Linux) | .\gradlew.bat bootRun (Windows)" -ForegroundColor White
if ($Mode -eq "public") {
    Write-Host "Public mode (Windows): cmd /c scripts\manual-start-public.cmd" -ForegroundColor White
}

if ($requiredFailures.Count -gt 0) {
    Write-Title "Result"
    Write-ErrLine ("Moi truong chua dat. Thieu: " + ($requiredFailures -join ", "))
    if (-not $shouldInstall) {
        Write-InfoLine "Chay lai voi -InstallMissing de thu cai tu dong."
    } else {
        Write-InfoLine "Neu vua cai xong ma van bao thieu, hay mo terminal moi va chay lai script."
    }
    exit 1
}

Write-Title "Result"
Write-Ok "Moi truong dat yeu cau de chay du an."
if (-not $shouldInstall) {
    Write-InfoLine "Co the dung -InstallMissing de script tu cai cac cong cu thieu tren may moi."
}
exit 0
