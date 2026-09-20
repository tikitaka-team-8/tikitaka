param(
    [switch]$PrepareMonitoring,
    [switch]$ForceRecreate,
    [switch]$RequireStageMetrics,
    [switch]$RequirePlatformDiagnostics,
    [ValidateSet('real', 'stub')][string]$PlatformMode = 'real',
    [ValidateSet(1000, 2000, 10000)][int]$Vus = 1000,
    [ValidateRange(0,120)][int]$StartupSpreadSeconds = 0,
    [ValidateSet(0,8192,16384)][int]$MaxConnections = 0,
    [ValidateSet(0, 100, 1000)][int]$AcceptCount = 0
)

$ErrorActionPreference = 'Stop'
if ($ForceRecreate -and -not $PrepareMonitoring) {
    throw '-ForceRecreate requires -PrepareMonitoring'
}
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$runner = Join-Path $PSScriptRoot 'run-vu.py'

Push-Location $repo
try {
    if ($PrepareMonitoring) {
        $current = docker inspect tikitaka-ticketing-service | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect current Ticketing container' }
        $current = @($current)[0]
        $settings = @{}
        foreach ($entry in $current.Config.Env) {
            $parts = $entry.Split('=', 2)
            $settings[$parts[0]] = $parts[1]
        }
        $settings['SERVER_TOMCAT_MBEANREGISTRY_ENABLED'] = 'true'
        if ($MaxConnections -gt 0) {
            $settings['SERVER_TOMCAT_MAX_CONNECTIONS'] = [string]$MaxConnections
        }
        $settings['CLIENTS_PLATFORM_SERVICE_URL'] = if ($PlatformMode -eq 'stub') { 'http://queue-platform-stub:8081' } else { 'http://platform-service:8081' }
        if ($AcceptCount -gt 0) {
            $settings['SERVER_TOMCAT_ACCEPT_COUNT'] = [string]$AcceptCount
        }
        # Compose interpolates dollar signs even in an override JSON file.
        foreach ($key in @($settings.Keys)) {
            $settings[$key] = $settings[$key].Replace('$', '$$')
        }
        # Preserve the running image and environment, including local service keys.
        # The temporary override contains credentials and must never be committed.
        $override = Join-Path ([IO.Path]::GetTempPath()) ('queue-observe-' + [guid]::NewGuid().ToString() + '.json')
        try {
            @{services=@{'ticketing-service'=@{image=$current.Image;environment=$settings}}} |
                ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $override -Encoding UTF8
            $recreateArgs = @()
            if ($ForceRecreate) { $recreateArgs = @('--force-recreate') }
            docker compose -f docker-compose.yml -f docker-compose.test.yml -f $override `
                up -d --no-deps --no-build --pull never --wait --wait-timeout 120 @recreateArgs ticketing-service
            if ($LASTEXITCODE -ne 0) { throw 'Ticketing monitoring setup failed' }
        }
        finally {
            if (Test-Path -LiteralPath $override) { Remove-Item -LiteralPath $override }
        }
        $updated = docker inspect tikitaka-ticketing-service | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Cannot verify Ticketing after preparation' }
        $updated = @($updated)[0]
        if ($updated.Image -ne $current.Image) { throw 'Unexpected image change during preparation' }
        if ($ForceRecreate -and ($updated.Id -eq $current.Id -or $updated.State.StartedAt -eq $current.State.StartedAt)) {
            throw 'Ticketing was not recreated; do not run the load'
        }
        Write-Host "Ticketing recreated=$($updated.Id -ne $current.Id) startedAt=$($updated.State.StartedAt)"
        $prom = (Invoke-WebRequest http://localhost:8082/actuator/prometheus -UseBasicParsing -TimeoutSec 10).Content
        if ($prom -notmatch '(?m)^tomcat_threads_busy_threads') {
            throw 'Tomcat thread metrics missing; do not run the load yet'
        }
        if ($prom -notmatch '(?m)^tomcat_connections_current_connections') {
            throw 'Tomcat connection metrics missing; do not run the load yet'
        }
        Write-Host 'Monitoring ready. Wait 180 seconds before the next load run. No load was executed.'
        return
    }
    $label = 'mentor-direct'
    $modeEnv = docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' tikitaka-ticketing-service
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify Platform route' }
    $route = @($modeEnv | Where-Object { $_ -like 'CLIENTS_PLATFORM_SERVICE_URL=*' })
    $expected = if ($PlatformMode -eq 'stub') { 'CLIENTS_PLATFORM_SERVICE_URL=http://queue-platform-stub:8081' } else { 'CLIENTS_PLATFORM_SERVICE_URL=http://platform-service:8081' }
    if (($route.Count -and $route[0] -ne $expected) -or ($PlatformMode -eq 'stub' -and -not $route.Count)) {
        throw 'Platform route does not match the selected mode; prepare this mode first.'
    }
    if ($AcceptCount -gt 0) {
        $configured = docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' tikitaka-ticketing-service
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Ticketing test settings' }
        if ($configured -notcontains "SERVER_TOMCAT_ACCEPT_COUNT=$AcceptCount") {
            throw "Run -PrepareMonitoring -AcceptCount $AcceptCount first. No load executed."
        }
        $label = "mentor-direct-backlog-$AcceptCount"
    }
    if ($Vus -gt 1000) { $label = "capacity-$Vus-backlog-$AcceptCount" }
    $prom = (Invoke-WebRequest http://localhost:8082/actuator/prometheus -UseBasicParsing -TimeoutSec 10).Content
    if ($MaxConnections -gt 0) {
        $match = [regex]::Match($prom, '(?m)^tomcat_connections_config_max_connections\{[^\r\n]*\}\s+([0-9.eE+-]+)')
        if (-not $match.Success -or [double]$match.Groups[1].Value -ne $MaxConnections) {
            throw 'Runtime Tomcat max-connections does not match the experiment. No load executed.'
        }
        $label += "-maxconn-$MaxConnections"
    }
    if ($RequirePlatformDiagnostics -and $prom -notmatch '(?m)^queue_platform_client_total') {
        throw 'Platform client diagnostics missing. Build with docker-compose.test.yml first. No load executed.'
    }
    if ($RequireStageMetrics -and $prom -notmatch '(?m)^queue_registration_stage_seconds_count') {
        throw 'Registration metrics missing. Build and apply the observed Ticketing image first. No load executed.'
    }
    if ($prom -notmatch '(?m)^tomcat_threads_busy_threads' -or $prom -notmatch '(?m)^tomcat_connections_current_connections') {
        throw 'Tomcat metrics missing. Run this script with -PrepareMonitoring first.'
    }
    Write-Host "`n===== Ticketing direct Queue registration: $Vus VU Spike ====="
    python -u $runner --mode spike --target ticketing --registration-only --platform-mode $PlatformMode --spike-vus $Vus `
        --startup-spread-seconds $StartupSpreadSeconds --label "$label-platform-$PlatformMode" --cleanup-db-fixtures
    if ($LASTEXITCODE -ne 0) {
        throw 'Direct Spike failed. Inspect result.json, tcp-delta.json, tomcat-samples.json and k6.log in the saved artifact.'
    }
}
finally {
    Pop-Location
}
