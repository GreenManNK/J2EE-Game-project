param(
    [switch]$NoLive
)

$ErrorActionPreference = "Stop"

$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
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

function Assert-FileMissing([string]$RelativePath) {
    $path = Join-Path $repoRoot $RelativePath
    Assert-True (-not (Test-Path $path)) ("File '{0}' van con ton tai" -f $RelativePath)
}

function Get-XmlOptionValue($ConfigurationNode, [string]$OptionName) {
    $node = $ConfigurationNode.option | Where-Object { $_.name -eq $OptionName } | Select-Object -First 1
    if (-not $node) {
        throw "Khong tim thay option '$OptionName'"
    }
    return [string]$node.value
}

function Assert-IntelliJRunConfig([string]$FileName, [string]$ExpectedScriptPath, [string]$ExpectedScriptOptions) {
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
    Assert-True ($scriptOptions -eq $ExpectedScriptOptions) ("SCRIPT_OPTIONS sai trong $relativePath. Expected: $ExpectedScriptOptions ; Actual: $scriptOptions")
    $interpreterFileName = [System.IO.Path]::GetFileName($interpreterPath)
    Assert-True ($interpreterFileName -ieq "cmd.exe") ("INTERPRETER_PATH sai trong $relativePath")
    Assert-True ($interpreterOptions -eq "/c") ("INTERPRETER_OPTIONS sai trong $relativePath")
}

function Assert-VsCodeTask([pscustomobject[]]$Tasks, [string]$Label, [string[]]$ExpectedArgs) {
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
    Assert-True ($args.Count -eq $ExpectedArgs.Count) ("Task '$Label' sai so luong args. Expected: $($ExpectedArgs.Count) ; Actual: $($args.Count)")
    for ($i = 0; $i -lt $ExpectedArgs.Count; $i++) {
        Assert-True ($args[$i] -eq $ExpectedArgs[$i]) ("Task '$Label' args[$i] sai. Expected: $($ExpectedArgs[$i]) ; Actual: $($args[$i])")
    }
}

function Invoke-CmdScript([string]$RelativePath, [string[]]$Arguments = @(), [switch]$CaptureOutput) {
    $fullPath = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path $fullPath)) {
        throw "Khong tim thay script: $RelativePath"
    }

    Write-Host ""
    $displayArgs = @($Arguments + @("--no-pause")) -join " "
    if ([string]::IsNullOrWhiteSpace($displayArgs)) {
        Write-Host ("> cmd /c {0}" -f $RelativePath) -ForegroundColor Cyan
    } else {
        Write-Host ("> cmd /c {0} {1}" -f $RelativePath, $displayArgs) -ForegroundColor Cyan
    }
    $cmdArgs = @("/c", $fullPath) + $Arguments + @("--no-pause")

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
        $cfQuickAlive = Test-ProcessAliveByPidFile "cloudflared.pid"
        $cfNamedAlive = Test-ProcessAliveByPidFile "cloudflared-named.pid"
        if (-not $appAlive -and -not $cfQuickAlive -and -not $cfNamedAlive) {
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

Write-Step "Kiem tra da gom ve 1 file CMD dieu khien"
Invoke-Check "scripts chi con 1 file CMD dieu khien" {
    $cmdFiles = @(Get-ChildItem -Path (Join-Path $repoRoot "scripts") -Filter "*.cmd" -File | Select-Object -ExpandProperty Name | Sort-Object)
    Assert-True ($cmdFiles.Count -eq 1) ("Thu muc scripts con qua nhieu CMD: " + ($cmdFiles -join ", "))
    Assert-True ($cmdFiles[0] -eq "manual-start.cmd") ("CMD dieu khien chinh khong dung ten manual-start.cmd")
} | Out-Null
Invoke-Check "scripts/entrypoints khong con file alias runtime" {
    $entrypointDir = Join-Path $repoRoot "scripts/entrypoints"
    if (-not (Test-Path $entrypointDir)) {
        return
    }
    $entrypointFiles = @(Get-ChildItem -Path $entrypointDir -File -ErrorAction SilentlyContinue)
    Assert-True ($entrypointFiles.Count -eq 0) ("Thu muc scripts/entrypoints van con file: " + (($entrypointFiles | Select-Object -ExpandProperty Name) -join ", "))
} | Out-Null
Invoke-Check "Legacy CMD wrappers da duoc go" {
    Assert-FileMissing "scripts/manual-start-public.cmd"
    Assert-FileMissing "scripts/manual-start-public-mysql.cmd"
    Assert-FileMissing "scripts/manual-start-public-postgres.cmd"
    Assert-FileMissing "scripts/manual-start-local.cmd"
    Assert-FileMissing "scripts/manual-start-docker.cmd"
    Assert-FileMissing "scripts/manual-status.cmd"
    Assert-FileMissing "scripts/manual-stop-all.cmd"
    Assert-FileMissing "scripts/manual-stop-docker.cmd"
    Assert-FileMissing "scripts/manual-verify-public-all.cmd"
    Assert-FileMissing "scripts/entrypoints/RUN.ps1"
} | Out-Null

Write-Step "Kiem tra nut bam IntelliJ (.run)"
Invoke-Check "IntelliJ Start (Default Public)" {
    Assert-IntelliJRunConfig "Start (Default Public).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd' '--no-pause'
} | Out-Null
Invoke-Check "IntelliJ Start Public (Quick Tunnel)" {
    Assert-IntelliJRunConfig "Start Public (Quick Tunnel).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd' 'start --public --no-pause'
} | Out-Null
Invoke-Check "IntelliJ Start Public (MySQL Standard)" {
    Assert-IntelliJRunConfig "Start Public (MySQL Standard).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd' 'start --public --mysql --no-pause'
} | Out-Null
Invoke-Check "IntelliJ Start Public (PostgreSQL)" {
    Assert-IntelliJRunConfig "Start Public (PostgreSQL).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd' 'start --public --postgres --no-pause'
} | Out-Null
Invoke-Check "IntelliJ Start Local (J2EE)" {
    Assert-IntelliJRunConfig "Start Local (J2EE).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd' 'start --local --no-pause'
} | Out-Null
Invoke-Check "IntelliJ Status (App + Tunnel)" {
    Assert-IntelliJRunConfig "Status (App + Tunnel).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd' 'status --no-pause'
} | Out-Null
Invoke-Check "IntelliJ Stop All (App + Tunnel)" {
    Assert-IntelliJRunConfig "Stop All (App + Tunnel).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd' 'stop --no-pause'
} | Out-Null
Invoke-Check "IntelliJ Verify Public (All-in-One)" {
    Assert-IntelliJRunConfig "Verify Public (All-in-One).run.xml" '$PROJECT_DIR$/scripts/manual-start.cmd' 'verify --no-pause'
} | Out-Null

Write-Step "Kiem tra nut bam VS Code tasks"
Invoke-Check "VS Code tasks.json public task mappings" {
    $tasksPath = Join-Path $repoRoot ".vscode/tasks.json"
    if (-not (Test-Path $tasksPath)) {
        throw "Khong tim thay .vscode/tasks.json"
    }
    $tasksDoc = Get-Content -Path $tasksPath -Raw | ConvertFrom-Json
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start (Default Public)" @("/c", "scripts\manual-start.cmd", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start Public (Quick Tunnel)" @("/c", "scripts\manual-start.cmd", "start", "--public", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start Public (MySQL Standard)" @("/c", "scripts\manual-start.cmd", "start", "--public", "--mysql", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start Public (PostgreSQL)" @("/c", "scripts\manual-start.cmd", "start", "--public", "--postgres", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start Local (J2EE)" @("/c", "scripts\manual-start.cmd", "start", "--local", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Status (App + Tunnel)" @("/c", "scripts\manual-start.cmd", "status", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Stop All (App + Tunnel)" @("/c", "scripts\manual-start.cmd", "stop", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Start Docker" @("/c", "scripts\manual-start.cmd", "start", "--docker", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Stop Docker" @("/c", "scripts\manual-start.cmd", "stop", "--docker", "--no-pause")
    Assert-VsCodeTask $tasksDoc.tasks "Game: Verify Public (All-in-One)" @("/c", "scripts\manual-start.cmd", "verify", "--no-pause")
} | Out-Null

if (-not $NoLive) {
    $started = $false
    $stopped = $false
    try {
        Write-Step "Kiem tra live PUBLIC flow (manual-start.cmd)"

        Invoke-Check "Pre-clean stop (manual-start.cmd stop) khong bi loi" {
            Invoke-CmdScript "scripts/manual-start.cmd" @("stop") | Out-Null
        } | Out-Null

        $startOk = Invoke-Check "Manual start public (scripts/manual-start.cmd start --public)" {
            Invoke-CmdScript "scripts/manual-start.cmd" @("start", "--public") | Out-Null
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

            Invoke-Check "Manual status (scripts/manual-start.cmd status) hien thong tin dang chay" {
                $statusLines = Invoke-CmdScript "scripts/manual-start.cmd" @("status") -CaptureOutput
                $status = Parse-StatusOutput $statusLines
                Ensure-StatusValue $status "APP_PROCESS_ALIVE" "1"
                Ensure-StatusValue $status "APP_LISTEN_8080" "1"
                Assert-True ($status.ContainsKey("ACTIVE_TUNNEL_MODE")) "Status output thieu ACTIVE_TUNNEL_MODE"
                $activeMode = [string]$status["ACTIVE_TUNNEL_MODE"]
                $validActiveMode = ($activeMode -eq "quick") `
                    -or ($activeMode -eq "named") `
                    -or ($activeMode -eq "runlocal") `
                    -or ($activeMode -eq "localtunnel")
                Assert-True $validActiveMode ("ACTIVE_TUNNEL_MODE khong hop le: " + $activeMode)
                if ($activeMode -eq "quick") {
                    Ensure-StatusValue $status "QUICK_TUNNEL_PROCESS_ALIVE" "1"
                } elseif ($activeMode -eq "named") {
                    Ensure-StatusValue $status "NAMED_TUNNEL_PROCESS_ALIVE" "1"
                } else {
                    Ensure-StatusValue $status "FALLBACK_TUNNEL_PROCESS_ALIVE" "1"
                }
                Assert-True ($status.ContainsKey("ACTIVE_PUBLIC_GAME_URL")) "Status output thieu ACTIVE_PUBLIC_GAME_URL"
                Assert-True (-not [string]::IsNullOrWhiteSpace([string]$status["ACTIVE_PUBLIC_GAME_URL"])) "ACTIVE_PUBLIC_GAME_URL rong khi dang chay"
            } | Out-Null
        }

        Invoke-Check "Manual stop (scripts/manual-start.cmd stop) dung app + tunnel" {
            Invoke-CmdScript "scripts/manual-start.cmd" @("stop") | Out-Null
            $script:stopped = $true
            Assert-True (Wait-ProcessesStopped 25) "App/tunnel chua dung sau scripts/manual-start.cmd stop"
        } | Out-Null

        Invoke-Check "Manual status sau khi stop hien process=0" {
            $statusLines = Invoke-CmdScript "scripts/manual-start.cmd" @("status") -CaptureOutput
            $status = Parse-StatusOutput $statusLines
            Ensure-StatusValue $status "APP_PROCESS_ALIVE" "0"
            Ensure-StatusValue $status "QUICK_TUNNEL_PROCESS_ALIVE" "0"
            Ensure-StatusValue $status "NAMED_TUNNEL_PROCESS_ALIVE" "0"
            Ensure-StatusValue $status "DOCKER_CONTAINER_RUNNING" "0"
        } | Out-Null
    } finally {
        if ($started -and -not $stopped) {
            try {
                Write-Host ""
                Write-Host "Dang cleanup (manual-start.cmd stop)..." -ForegroundColor Yellow
                Invoke-CmdScript "scripts/manual-start.cmd" @("stop") | Out-Null
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
