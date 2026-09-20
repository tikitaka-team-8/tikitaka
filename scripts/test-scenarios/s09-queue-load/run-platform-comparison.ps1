param(
    [ValidateRange(1,3)][int]$Rounds = 1,
    [ValidateSet(1000,2000,10000)][int]$Vus = 1000,
    [ValidateSet('none','delay500','error503')][string]$Fault = 'none'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$runner = Join-Path $PSScriptRoot 'run-ticketing-direct-spike.ps1'
Push-Location $repo
try {
    # Guard against overwriting a nonstandard local Platform route.
    $envLines = docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' tikitaka-ticketing-service
    if ($LASTEXITCODE -ne 0) { throw 'Ticketing is not available' }
    $route = @($envLines | Where-Object { $_ -like 'CLIENTS_PLATFORM_SERVICE_URL=*' })
    if ($route.Count -and $route[0] -ne 'CLIENTS_PLATFORM_SERVICE_URL=http://platform-service:8081') {
        throw 'Comparison requires the normal Platform route before starting'
    }
    $occupied = docker ps -aq --filter 'name=^tikitaka-queue-platform-stub$'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect existing stub container' }
    if ($occupied) { throw 'Stub container already exists; finish its existing experiment first' }
    try {
        docker compose -f docker-compose.yml -f docker-compose.test.yml --profile queue-dependency-test up -d --no-deps --wait --wait-timeout 60 queue-platform-stub
        if ($LASTEXITCODE -ne 0) { throw 'Stub startup failed' }
        $faultConfig = switch ($Fault) {
            'delay500' { '{"delayMs":500,"status":200}' }
            'error503' { '{"delayMs":0,"status":503}' }
            default { '{"delayMs":0,"status":200}' }
        }
        $faultConfig | docker exec -i tikitaka-queue-platform-stub python -c "import sys,urllib.request; print(urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8081/fault',data=sys.stdin.buffer.read(),headers={'Content-Type':'application/json'})).read().decode())"
        if ($LASTEXITCODE -ne 0) { throw 'Fault configuration failed' }
        for ($round = 1; $round -le $Rounds; $round++) {
            # Reverse order on alternate pairs to reduce fixed-order bias.
            $modes = if ($round % 2) { @('real','stub') } else { @('stub','real') }
            if ($Fault -ne 'none') { $modes = @('stub') }
            foreach ($mode in $modes) {
                Write-Host "`n=== Platform comparison $round/$Rounds mode=$mode fault=$Fault ==="
                & $runner -PrepareMonitoring -ForceRecreate -AcceptCount 1000 -PlatformMode $mode
                Start-Sleep -Seconds 180
                try {
                    & $runner -AcceptCount 1000 -PlatformMode $mode -Vus $Vus -RequireStageMetrics -RequirePlatformDiagnostics
                } catch {
                    Write-Warning "Measurement failed: $($_.Exception.Message). Preserve artifacts for analysis."
                }
                Start-Sleep -Seconds 60
            }
        }
    } finally {
        # Restore real calls even when a measurement fails. Do not remove stub if restoration fails.
        & $runner -PrepareMonitoring -ForceRecreate -AcceptCount 1000 -PlatformMode real
        docker compose -f docker-compose.yml -f docker-compose.test.yml --profile queue-dependency-test rm -s -f queue-platform-stub
        if ($LASTEXITCODE -ne 0) { throw 'Stub cleanup failed' }
    }
} finally {
    Pop-Location
}
