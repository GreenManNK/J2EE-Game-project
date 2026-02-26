param(
    [ValidateSet("local", "public")]
    [string]$Mode = "local",
    [ValidateSet("auto", "h2", "mysql", "postgres")]
    [string]$Db = "auto",
    [switch]$CheckOnly
)

$doctor = Join-Path $PSScriptRoot "dev-env-doctor.ps1"
$params = @{
    Mode = $Mode
    Db = $Db
    InstallMissing = $true
}
if ($CheckOnly) {
    $params.CheckOnly = $true
}

& $doctor @params
exit $LASTEXITCODE
