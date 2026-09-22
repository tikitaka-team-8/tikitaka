param(
    [ValidateSet(1000,2000,10000)][int]$Vus = 1000,
    [ValidateRange(0,120)][int]$StartupSpreadSeconds = 0
)
$ErrorActionPreference = 'Stop'
# Use the already running common test environment. Never replace its image or configuration.
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Push-Location $repo
try {
    python -u (Join-Path $PSScriptRoot 'run-vu.py') --mode spike --target ticketing --registration-only `
        --spike-vus $Vus --startup-spread-seconds $StartupSpreadSeconds --label direct-register --cleanup-db-fixtures
    if ($LASTEXITCODE -ne 0) { throw 'Direct registration failed; inspect saved results.' }
} finally { Pop-Location }
