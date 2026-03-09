$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $repoRoot "app-prod.pid"

function Get-ListeningPids([int]$Port) {
    try {
        return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique)
    } catch {
        return @()
    }
}

function Stop-ProcessIds([int[]]$Ids) {
    $stopped = New-Object System.Collections.Generic.List[string]
    foreach ($id in ($Ids | Where-Object { $_ -gt 0 } | Select-Object -Unique)) {
        try {
            Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
            $stopped.Add([string]$id) | Out-Null
        } catch {
        }
    }
    return $stopped
}

if (-not (Test-Path $pidFile)) {
    $listenOnlyIds = Get-ListeningPids -Port 8080
    if ($listenOnlyIds.Count -eq 0) {
        Write-Output "Khong tim thay app-prod.pid"
        exit 0
    }
    $stoppedByPort = Stop-ProcessIds -Ids $listenOnlyIds
    if ($stoppedByPort.Count -gt 0) {
        Write-Output ("Da dung app production theo PID listen 8080: " + ($stoppedByPort -join ", "))
    } else {
        Write-Output "Khong dung duoc app production dang listen 8080"
    }
    exit 0
}

$appPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
$targetIds = New-Object System.Collections.Generic.List[int]
if ($appPid) {
    $parsedPid = 0
    if ([int]::TryParse([string]$appPid, [ref]$parsedPid)) {
        $targetIds.Add($parsedPid) | Out-Null
    }
}
$listenIds = Get-ListeningPids -Port 8080
foreach ($listenId in $listenIds) {
    $targetIds.Add([int]$listenId) | Out-Null
}

$stopped = Stop-ProcessIds -Ids $targetIds.ToArray()
if ($stopped.Count -gt 0) {
    Write-Output ("Da dung app production (PID " + ($stopped -join ", ") + ")")
} else {
    Write-Output "Khong dung duoc app production (co the da tat)"
}

if ((Get-ListeningPids -Port 8080).Count -eq 0) {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}
