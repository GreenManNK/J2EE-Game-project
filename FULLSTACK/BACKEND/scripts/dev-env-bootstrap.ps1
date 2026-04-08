param(
    [ValidateSet("local", "public")]
    [string]$Mode = "local",
    [ValidateSet("auto", "h2", "mysql", "postgres")]
    [string]$Db = "auto",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$schemaVersion = 1
$stateDir = Join-Path $repoRoot ".data\dev-env"
$stateFile = Join-Path $stateDir ("bootstrap-" + $Mode + "-" + $Db + ".json")

function Write-InfoLine([string]$Text) { Write-Host "[INFO] $Text" -ForegroundColor Gray }
function Write-Ok([string]$Text) { Write-Host "[OK] $Text" -ForegroundColor Green }

function Test-BootstrapStateValid {
    param([string]$Path, [int]$ExpectedSchema)
    if (-not (Test-Path $Path)) { return $false }
    try {
        $raw = Get-Content -Raw $Path
        if ([string]::IsNullOrWhiteSpace($raw)) { return $false }
        $state = $raw | ConvertFrom-Json
        if ($state -and ($null -ne $state.schemaVersion)) {
            return ([int]$state.schemaVersion -eq $ExpectedSchema)
        }
    } catch {
    }
    if ($raw -match '"schemaVersion"\s*:\s*(?<v>\d+)') {
        return ([int]$Matches.v -eq $ExpectedSchema)
    }
    return $false
}

if ((-not $Force) -and (Test-BootstrapStateValid -Path $stateFile -ExpectedSchema $schemaVersion)) {
    Write-Ok "Bootstrap da hoan tat truoc do. Bo qua buoc chuan doan/cai dat."
    Write-InfoLine "Neu muon chay lai, dung -Force."
    exit 0
}

if (-not (Test-Path $stateDir)) {
    New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
}

Write-InfoLine "Chay bootstrap moi truong (lan dau hoac bi ep chay lai)..."
$setupScript = Join-Path $PSScriptRoot "dev-env-setup.ps1"
& $setupScript -Mode $Mode -Db $Db
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$state = [ordered]@{
    schemaVersion = $schemaVersion
    mode = $Mode
    db = $Db
    completedAtUtc = ([DateTime]::UtcNow.ToString("o"))
    os = [Environment]::OSVersion.ToString()
    machine = $env:COMPUTERNAME
}
$json = $state | ConvertTo-Json -Depth 4
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($stateFile, $json, $utf8NoBom)

Write-Ok "Bootstrap moi truong hoan tat. Cac lan chay sau se bo qua buoc nay."
Write-InfoLine "State file: $stateFile"
exit 0
