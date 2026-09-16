param([switch]$SkipWarmup)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$runner = Join-Path $PSScriptRoot 'run-vu.py'
$series = Join-Path $repo ('artifacts/queue-load/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-polling-series')
New-Item -ItemType Directory -Path $series -ErrorAction Stop | Out-Null
$results = @()

function Get-AppIdentity {
    $identity = @(docker inspect --format '{{.Name}} {{.Image}} {{.State.StartedAt}}' tikitaka-gateway tikitaka-ticketing-service tikitaka-platform-service)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect apps. Start the existing local environment first.' }
    return ($identity -join "`n")
}

Push-Location $repo
try {
    $identity = Get-AppIdentity
    $plan = @()
    if (-not $SkipWarmup) { $plan += [pscustomobject]@{ label = 'warmup'; vus = 50; poll = 2 } }
    foreach ($poll in @(1, 2, 5)) { $plan += [pscustomobject]@{ label = "poll-$poll"; vus = 300; poll = $poll } }
    foreach ($step in $plan) {
        if ((Get-AppIdentity) -ne $identity) { throw 'App image/start time changed. Do not mix runs.' }
        $log = Join-Path $series ($step.label + '.log')
        Write-Host "Starting $($step.label): VUs=$($step.vus), polling=$($step.poll)s"
        # Windows PowerShell 5.1 converts native stderr to ErrorRecords. Preserve the
        # traceback first, then stop explicitly on the Python exit code below.
        try {
            $ErrorActionPreference = 'Continue'
            python -u $runner --vus $step.vus --poll-seconds $step.poll 2>&1 | Tee-Object -FilePath $log
            $exitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = 'Stop' }
        $resultLine = Get-Content -LiteralPath $log | Where-Object { $_ -match '^Results: ' } | Select-Object -First 1
        $resultPath = if ($resultLine) { $resultLine -replace '^Results: ', '' } else { $null }
        $results += [pscustomobject]@{ role = $step.label; vus = $step.vus; pollSeconds = $step.poll; exitCode = $exitCode; resultPath = $resultPath }
        [ordered]@{ appIdentity = $identity; runs = $results } | ConvertTo-Json -Depth 5 |
            Set-Content -LiteralPath (Join-Path $series 'series.json') -Encoding utf8
        if ($exitCode -ne 0) { throw "Run failed. Remaining steps cancelled. Evidence: $series" }
        if (-not $resultPath) { throw 'Runner result path missing; remaining steps cancelled.' }
        $stage = Join-Path $resultPath ([string]$step.vus)
        $summary = Get-Content -LiteralPath (Join-Path $stage 'result.json') -Raw | ConvertFrom-Json
        $cleanup = Get-Content -LiteralPath (Join-Path $stage 'cleanup.json') -Raw | ConvertFrom-Json
        $recovery = Get-Content -LiteralPath (Join-Path $stage 'recovery.json') -Raw | ConvertFrom-Json
        if ($summary.failed -ne 0 -or $summary.started -ne $summary.completed -or -not $cleanup.redisCleaned -or $recovery.stable -lt 3) {
            throw 'Result/recovery/cleanup verification failed; remaining steps cancelled.'
        }
        if ((Get-AppIdentity) -ne $identity) { throw 'App image/start time changed during run; compare with caution.' }
    }
    Write-Host "Polling comparison complete: $series"
} finally { Pop-Location }
