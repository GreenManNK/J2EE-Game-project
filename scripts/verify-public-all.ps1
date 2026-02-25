param(
    [switch]$NoLive
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$failures = New-Object System.Collections.Generic.List[string]
$passes = New-Object System.Collections.Generic.List[string]

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "=== $Message ==="
}

function Add-Pass([string]$Message) {
    $passes.Add($Message) | Out-Null
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Add-Fail([string]$Message) {
    $failures.Add($Message) | Out-Null
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Invoke-Check([string]$Name, [scriptblock]$Action) {
    try {
        & $Action
        Add-Pass $Name
        return $true
    } catch {
        $detail = $_.Exception.Message
        if ($_.ScriptStackTrace) {
            $detail = "$detail"
        }
        Add-Fail ("{0} -> {1}" -f $Name, $detail)
        return $false
    }
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Get-Text([string]$RelativePath) {
    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path $path)) {
        throw "Khong tim thay file: $RelativePath"
    }
    return Get-Content -Path $path -Raw
}

function Assert-FileContains([string]$RelativePath, [string]$Pattern) {
    $text = Get-Text $RelativePath
    Assert-True ($text -match $Pattern) ("File '{0}' khong chua mau can thiet: {1}" -f $RelativePath, $Pattern)
}

function Get-XmlOptionValue($ConfigurationNode, [string]$OptionName) {
    $node = $ConfigurationNode.option | Where-Object { $_.name -eq $OptionName } | Select-Object -First 1
    if (-not $node) {
        throw "Khong tim thay option '$OptionName'"
    }
    return [string]$node.value
}

function Assert-IntelliJRunConfig([string]$FileName, [string]$ExpectedScriptPath) {
    $relativePath = Join-Path ".run" $FileName
    $raw = Get-Text $relativePath
    [xml]$xml = $raw
    $config = $xml.component.configuration
    if (-not $config) {
        throw "Cau truc XML khong hop le trong $relativePath"
    }

    $scriptPath = Get-XmlOptionValue $config "SCRIPT_PATH"
    $scriptOptions = Get-XmlOptionValue $config "SCRIPT_OPTIONS"
    $interpreterPath = Get-XmlOptionValue $config "INTERPRETER_PATH"
    $interpreterOptions = Get-XmlOptionValue $config "INTERPRETER_OPTIONS"

    Assert-True ($scriptPath -eq $ExpectedScriptPath) ("SCRIPT_PATH sai trong $relativePath. Expected: $ExpectedScriptPath ; Actual: $scriptPath")
    Assert-True ($scriptOptions -eq "--no-pause") ("SCRIPT_OPTIONS sai trong $relativePath")
    Assert-True ($interpreterPath -eq "cmd.exe") ("INTERPRETER_PATH sai trong $relativePath")
    Assert-True ($interpreterOptions -eq "/c") ("INTERPRETER_OPTIONS sai trong $relativePath")
}

function Assert-VsCodeTask([pscustomobject[]]$Tasks, [string]$Label, [string]$ExpectedScript) {
    $task = $Tasks | Where-Object { $_.label -eq $Label } | Select-Object -First 1
    if (-not $task) {
        throw "Khong tim thay VS Code task '$Label'"
    }
    Assert-True ($task.command -eq "cmd") ("Task '$Label' phai dung command=cmd")
    $argsProperty = $task.PSObject.Properties["args"]
    if (-not $argsProperty) {
        throw "Task '$Label' khong co property 'args'"
    }
    $args = @($argsProperty.Value)
    Assert-True ($args.Count -ge 3) ("Task '$Label' thieu args")
    Assert-True ($args[0] -eq "/c") ("Task '$Label' args[0] phai la /c")
    Assert-True ($args[1] -eq ($ExpectedScript -replace '/', '\\')) ("Task '$Label' args[1] sai. Expected: $ExpectedScript")
    Assert-True ($args -contains "--no-pause") ("Task '$Label' thieu --no-pause")
}

function Invoke-CmdScript([string]$RelativePath, [switch]$CaptureOutput) {
    $fullPath = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path $fullPath)) {
        throw "Khong tim thay script: $RelativePath"
    }

    Write-Host ""
    Write-Host ("> cmd /c {0} --no-pause" -f $RelativePath) -ForegroundColor Cyan
    $cmdArgs = @("/c", $fullPath, "--no-pause")

    if ($CaptureOutput) {
        $output = & cmd.exe @cmdArgs 2>&1
        $exitCode = $LASTEXITCODE
        $output | ForEach-Object { Write-Host $_ }
        if ($exitCode -ne 0) {
            throw "Lenh '$RelativePath' tra ve ma loi $exitCode"
        }
        return ,$output
    }

    & cmd.exe @cmdArgs
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Lenh '$RelativePath' tra ve ma loi $exitCode"
    }
    return @()
}

function Parse-StatusOutput([object[]]$Lines) {
    $map = @{}
    foreach ($line in $Lines) {
        $text = [string]$line
        if ($text -match '^([A-Z0-9_]+)=(.*)$') {
            $map[$matches[1]] = $matches[2]
        }
    }
    return $map
}

function Read-KeyValueFile([string]$RelativePath) {
    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path $path)) {
        throw "Khong tim thay file: $RelativePath"
    }
    $data = @{}
    foreach ($line in Get-Content -Path $path) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line.StartsWith("#")) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -lt 0) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if ($key) {
            $data[$key] = $value
        }
    }
    return $data
}

function Test-HttpOk([string]$Url, [int]$TimeoutSec = 10) {
    try {
        $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        return $res.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Test-ProcessAliveByPidFile([string]$RelativePidFile) {
    $pidPath = Join-Path $repoRoot $RelativePidFile
    if (-not (Test-Path $pidPath)) {
        return $false
    }
    $raw = Get-Content -Path $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $false
    }
    $pidValue = 0
    if (-not [int]::TryParse($raw.Trim(), [ref]$pidValue)) {
        return $false
    }
    try {
        $null = Get-Process -Id $pidValue -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

function Wait-ProcessesStopped([int]$TimeoutSeconds = 20) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $appAlive = Test-ProcessAliveByPidFile "app-prod.pid"
        $cfAlive = Test-ProcessAliveByPidFile "cloudflared.pid"
        if (-not $appAlive -and -not $cfAlive) {
            return $true
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

function Ensure-StatusValue($StatusMap, [string]$Key, [string]$Expected) {
    if (-not $StatusMap.ContainsKey($Key)) {
        throw "Status output thieu key '$Key'"
    }
    $actual = [string]$StatusMap[$Key]
    if ($actual -ne $Expected) {
        throw "Status '$Key' = '$actual' (expected '$Expected')"
    }
}

Write-Step "Kiem tra alias script (root -> manual)"
Invoke-Check "RUN_PUBLIC.cmd goi manual-start-public.cmd" {
    Assert-FileContains "RUN_PUBLIC.cmd" '(?i)manual-start-public\.cmd'
} | Out-Null
Invoke-Check "RUN_PUBLIC_MYSQL.cmd goi manual-start-public-mysql.cmd" {
    Assert-FileContains "RUN_PUBLIC_MYSQL.cmd" '(?i)manual-start-public-mysql\.cmd'
} | Out-Null
Invoke-Check "RUN_PUBLIC_POSTGRES.cmd goi manual-start-public-postgres.cmd" {
    Assert-FileContains "RUN_PUBLIC_POSTGRES.cmd" '(?i)manual-start-public-postgres\.cmd'
} | Out-Null
Invoke-Check "STATUS_PUBLIC.cmd goi manual-status.cmd" {
    Assert-FileContains "STATUS_PUBLIC.cmd" '(?i)manual-status\.cmd'
} | Out-Null
Invoke-Check "STOP_PUBLIC.cmd goi manual-stop-all.cmd" {
    Assert-FileContains "STOP_PUBLIC.cmd" '(?i)manual-stop-all\.cmd'
} | Out-Null

Write-Step "Kiem tra nut bam IntelliJ (.run)"
Invoke-Check "IntelliJ Start (Default Public)" {
    Assert-IntelliJRunConfig "Start (Default Public).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd'
} | Out-Null
Invoke-Check "IntelliJ Start Public (Quick Tunnel)" {
    Assert-IntelliJRunConfig "Start Public (Quick Tunnel).run.xml" '$PROJECT_DIR$/scripts/manual-start-public.cmd'
} | Out-Null
Invoke-Check "IntelliJ Start Public (MySQL Standard)" {
    Assert-IntelliJRunConfig "Start Public (MySQL Standard).run.xml" '$PROJECT_DIR$/scripts/manual-start-public-mysql.cmd'
} | Out-Null
Invoke-Check "IntelliJ Start Public (PostgreSQL)" {
    Assert-IntelliJRunConfig "Start Public (PostgreSQL).run.xml" '$PROJECT_DIR$/scripts/manual-start-public-postgres.cmd'
} | Out-Null
Invoke-Check "IntelliJ Status (App + Tunnel)" {
    Assert-IntelliJRunConfig "Status (App + Tunnel).run.xml" '$PROJECT_DIR$/scripts/manual-status.cmd'
} | Out-Null
Invoke-Check "IntelliJ Stop All (App + Tunnel)" {
    Assert-IntelliJRunConfig "Stop All (App + Tunnel).run.xml" '$PROJECT_DIR$/scripts/manual-stop-all.cmd'
} | Out-Null

Write-Step "Kiem tra nut bam VS Code tasks"
Invoke-Check "VS Code tasks.json public task mappings" {
    $tasksPath = Join-Path $repoRoot ".vscode/tasks.json"
    if (-not (Test-Path $tasksPath)) {
        throw "Khong tim thay .vscode/tasks.json"
    }
    $tasksDoc = Get-Content -Path $tasksPath -Raw | ConvertFrom-Json
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start (Default Public)" "scripts\manual-start.cmd"
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start Public (Quick Tunnel)" "scripts\manual-start-public.cmd"
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start Public (MySQL Standard)" "scripts\manual-start-public-mysql.cmd"
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start Public (PostgreSQL)" "scripts\manual-start-public-postgres.cmd"
    Assert-VsCodeTask $tasksDoc.tasks "Game: Status (App + Tunnel)" "scripts\manual-status.cmd"
    Assert-VsCodeTask $tasksDoc.tasks "Game: Stop All (App + Tunnel)" "scripts\manual-stop-all.cmd"
} | Out-Null

if (-not $NoLive) {
    $started = $false
    $stopped = $false
    try {
        Write-Step "Kiem tra live PUBLIC flow (manual + root wrapper)"

        Invoke-Check "Pre-clean stop (manual-stop-all.cmd) khong bi loi" {
            Invoke-CmdScript "scripts/manual-stop-all.cmd" | Out-Null
        } | Out-Null

        $startOk = Invoke-Check "Manual start public (scripts/manual-start-public.cmd)" {
            Invoke-CmdScript "scripts/manual-start-public.cmd" | Out-Null
            $script:started = $true
        }

        if ($startOk) {
            Invoke-Check "public-game-url.txt duoc tao va co PUBLIC_GAME_URL" {
                $info = Read-KeyValueFile "public-game-url.txt"
                Assert-True ($info.ContainsKey("PUBLIC_GAME_URL")) "Thieu PUBLIC_GAME_URL trong public-game-url.txt"
                Assert-True (-not [string]::IsNullOrWhiteSpace([string]$info["PUBLIC_GAME_URL"])) "PUBLIC_GAME_URL rong"
                Assert-True ($info.ContainsKey("LOCAL_GAME_URL")) "Thieu LOCAL_GAME_URL trong public-game-url.txt"
                Assert-True (-not [string]::IsNullOrWhiteSpace([string]$info["LOCAL_GAME_URL"])) "LOCAL_GAME_URL rong"
            } | Out-Null

            Invoke-Check "Kiem tra local/public URL va ws/info truy cap duoc" {
                $info = Read-KeyValueFile "public-game-url.txt"
                $localGameUrl = [string]$info["LOCAL_GAME_URL"]
                $publicGameUrl = [string]$info["PUBLIC_GAME_URL"]
                $publicBaseUrl = [string]$info["PUBLIC_BASE_URL"]
                $ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                $localWsUrl = ($localGameUrl.TrimEnd("/") + "/ws/info?t=" + $ts)
                $publicWsUrl = if ([string]::IsNullOrWhiteSpace($publicBaseUrl)) {
                    $publicGameUrl.TrimEnd("/") + "/ws/info?t=" + $ts
                } else {
                    $publicBaseUrl.TrimEnd("/") + "/Game/ws/info?t=" + $ts
                }

                Assert-True (Test-HttpOk $localGameUrl 10) ("Khong truy cap duoc local game URL: $localGameUrl")
                Assert-True (Test-HttpOk $publicGameUrl 10) ("Khong truy cap duoc public game URL: $publicGameUrl")
                Assert-True (Test-HttpOk $localWsUrl 10) ("Khong truy cap duoc local ws/info: $localWsUrl")
                Assert-True (Test-HttpOk $publicWsUrl 10) ("Khong truy cap duoc public ws/info: $publicWsUrl")
            } | Out-Null

            Invoke-Check "Manual status (scripts/manual-status.cmd) hien thong tin dang chay" {
                $statusLines = Invoke-CmdScript "scripts/manual-status.cmd" -CaptureOutput
                $status = Parse-StatusOutput $statusLines
                Ensure-StatusValue $status "APP_PROCESS_ALIVE" "1"
                Ensure-StatusValue $status "APP_LISTEN_8080" "1"
                Ensure-StatusValue $status "QUICK_TUNNEL_PROCESS_ALIVE" "1"
                Assert-True ($status.ContainsKey("QUICK_TUNNEL_URL")) "Status output thieu QUICK_TUNNEL_URL"
                Assert-True (-not [string]::IsNullOrWhiteSpace([string]$status["QUICK_TUNNEL_URL"])) "QUICK_TUNNEL_URL rong khi dang chay"
            } | Out-Null

            Invoke-Check "Root status wrapper (STATUS_PUBLIC.cmd) chay thanh cong" {
                $statusLines = Invoke-CmdScript "STATUS_PUBLIC.cmd" -CaptureOutput
                $status = Parse-StatusOutput $statusLines
                Ensure-StatusValue $status "APP_PROCESS_ALIVE" "1"
                Ensure-StatusValue $status "QUICK_TUNNEL_PROCESS_ALIVE" "1"
            } | Out-Null
        }

        Invoke-Check "Root stop wrapper (STOP_PUBLIC.cmd) dung app + tunnel" {
            Invoke-CmdScript "STOP_PUBLIC.cmd" | Out-Null
            $script:stopped = $true
            Assert-True (Wait-ProcessesStopped 25) "App/tunnel chua dung sau STOP_PUBLIC.cmd"
        } | Out-Null

        Invoke-Check "Root status wrapper sau khi stop hien process=0" {
            $statusLines = Invoke-CmdScript "STATUS_PUBLIC.cmd" -CaptureOutput
            $status = Parse-StatusOutput $statusLines
            Ensure-StatusValue $status "APP_PROCESS_ALIVE" "0"
            Ensure-StatusValue $status "QUICK_TUNNEL_PROCESS_ALIVE" "0"
        } | Out-Null
    } finally {
        if ($started -and -not $stopped) {
            try {
                Write-Host ""
                Write-Host "Dang cleanup (manual-stop-all.cmd)..." -ForegroundColor Yellow
                Invoke-CmdScript "scripts/manual-stop-all.cmd" | Out-Null
            } catch {
                Write-Host ("Cleanup that bai: {0}" -f $_.Exception.Message) -ForegroundColor Yellow
            }
        }
    }
} else {
    Write-Step "Bo qua live PUBLIC flow (NoLive)"
    Add-Pass "NoLive mode: chi kiem tra alias + cau hinh nut bam"
}

Write-Host ""
Write-Host "========== TONG KET =========="
Write-Host ("PASS: {0}" -f $passes.Count)
Write-Host ("FAIL: {0}" -f $failures.Count)
if ($failures.Count -gt 0) {
    Write-Host ""
    Write-Host "Danh sach loi:" -ForegroundColor Red
    foreach ($f in $failures) {
        Write-Host ("- {0}" -f $f) -ForegroundColor Red
    }
    exit 1
}
Write-Host "Tat ca kiem tra PUBLIC da dat." -ForegroundColor Green
exit 0
