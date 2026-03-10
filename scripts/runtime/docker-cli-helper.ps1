function Add-DockerDesktopToPath {
    $candidates = @()
    if ($env:OS -eq "Windows_NT") {
        $candidates += "C:\Program Files\Docker\Docker\resources\bin"
    }

    foreach ($candidate in $candidates) {
        if (-not (Test-Path $candidate)) {
            continue
        }

        $present = $false
        foreach ($entry in ($env:Path -split ";")) {
            if ($entry.Trim().TrimEnd("\") -ieq $candidate.TrimEnd("\")) {
                $present = $true
                break
            }
        }

        if (-not $present) {
            if ([string]::IsNullOrWhiteSpace($env:Path)) {
                $env:Path = $candidate
            } else {
                $env:Path += ";" + $candidate
            }
        }
    }
}

function Get-DockerCliCommand {
    Add-DockerDesktopToPath

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        return $docker.Source
    }

    if ($env:OS -eq "Windows_NT") {
        $candidate = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    return $null
}

function Get-DockerComposeLegacyCommand {
    Add-DockerDesktopToPath

    $legacy = Get-Command docker-compose -ErrorAction SilentlyContinue
    if ($legacy) {
        return $legacy.Source
    }

    if ($env:OS -eq "Windows_NT") {
        $candidate = "C:\Program Files\Docker\Docker\resources\bin\docker-compose.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    return $null
}

function Test-DockerComposeV2Available {
    $dockerCommand = Get-DockerCliCommand
    if (-not $dockerCommand) {
        return $false
    }

    try {
        & $dockerCommand compose version | Out-Null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    }
}

function Invoke-DockerCompose {
    param([string[]]$CommandArgs)

    $dockerCommand = Get-DockerCliCommand
    if (Test-DockerComposeV2Available) {
        & $dockerCommand compose @CommandArgs | Out-Host
        $exitCode = $LASTEXITCODE
        return $exitCode
    }

    $legacy = Get-DockerComposeLegacyCommand
    if ($legacy) {
        & $legacy @CommandArgs | Out-Host
        $exitCode = $LASTEXITCODE
        return $exitCode
    }

    throw "Khong tim thay Docker Compose (docker compose hoac docker-compose)."
}
