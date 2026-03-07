param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ArgsForward
)

$ErrorActionPreference = "Stop"
$repoRoot = $PSScriptRoot
$cmdScript = Join-Path $repoRoot "RUN.cmd"

if (-not (Test-Path $cmdScript)) {
    throw "Khong tim thay script: $cmdScript"
}

& cmd.exe /c $cmdScript @ArgsForward
exit $LASTEXITCODE
