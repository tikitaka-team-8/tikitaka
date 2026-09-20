param(
    [ValidateSet(2000,10000)][int]$Vus = 10000,
    [ValidateRange(0,120)][int]$StartupSpreadSeconds = 0,
    [ValidateSet(0,8192,16384)][int]$MaxConnections = 0
)

$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'run-ticketing-direct-spike.ps1'
# One explicit load level per invocation. Never advance automatically after a failure.
# Keep the existing observed image, actual Platform, quota/batch 50 and 5s timeout.
$originalMax = 0
if ($MaxConnections -gt 0) {
    $prom = (Invoke-WebRequest http://localhost:8082/actuator/prometheus -UseBasicParsing -TimeoutSec 10).Content
    $match = [regex]::Match($prom, '(?m)^tomcat_connections_config_max_connections\{[^\r\n]*\}\s+([0-9.eE+-]+)')
    if (-not $match.Success) { throw 'Cannot read original max-connections' }
    $originalMax = [int][double]$match.Groups[1].Value
    if ($originalMax -notin @(8192,16384)) { throw 'Unsupported original max-connections; preserve current configuration' }
}
try {
    & $runner -PrepareMonitoring -ForceRecreate -AcceptCount 1000 -PlatformMode real -MaxConnections $MaxConnections
    Start-Sleep -Seconds 180
    & $runner -AcceptCount 1000 -PlatformMode real -Vus $Vus -StartupSpreadSeconds $StartupSpreadSeconds -MaxConnections $MaxConnections -RequireStageMetrics -RequirePlatformDiagnostics
} finally {
    if ($originalMax -gt 0 -and $originalMax -ne $MaxConnections) {
        Write-Host "Restoring runtime max-connections=$originalMax (explicit environment override)."
        & $runner -PrepareMonitoring -ForceRecreate -AcceptCount 1000 -PlatformMode real -MaxConnections $originalMax
    }
}
Write-Host 'Probe completed. Review result, TCP, Platform, Tomcat, Redis and k6 resource evidence before increasing VUs.'
