$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$revision = git -C $repo rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve HEAD' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$snapshot = Join-Path $repo "artifacts/queue-build/$stamp"
New-Item -ItemType Directory -Path $snapshot -Force | Out-Null
# Build an immutable copy so edited/untracked instrumentation is included in the evidence.
$paths = @('gradlew', 'settings.gradle', 'build.gradle', 'gradle',
    'gateway/build.gradle', 'payment-notification-service/build.gradle',
    'platform-service/build.gradle', 'ticketing-service/build.gradle',
    'ticketing-service/Dockerfile', 'ticketing-service/src')
foreach ($relative in $paths) {
    $target = Join-Path $snapshot $relative
    New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo $relative) -Destination $target -Recurse
}
# All files come from this directory; prefix removal also works on Windows PowerShell 5.1.
$snapshotPrefix = [IO.Path]::GetFullPath($snapshot).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
$manifest = @(Get-ChildItem -LiteralPath $snapshot -File -Recurse | Sort-Object FullName | ForEach-Object {
    if (-not $_.FullName.StartsWith($snapshotPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Manifest file is outside the source snapshot'
    }
    [ordered]@{ path = $_.FullName.Substring($snapshotPrefix.Length); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
})
$manifestPath = Join-Path $snapshot 'source-manifest.json'
$manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding utf8
$hash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$image = "tikitaka-ticketing-observed:$stamp"
docker build --label "dev.tikitaka.base-revision=$revision" --label 'dev.tikitaka.working-tree=true' `
    --label "dev.tikitaka.source-sha256=$hash" -f (Join-Path $snapshot 'ticketing-service/Dockerfile') -t $image $snapshot
if ($LASTEXITCODE -ne 0) { throw 'Observed build failed' }
$override = Join-Path $snapshot 'compose.observed.yml'
@"
services:
  ticketing-service:
    image: $image
    environment:
      SPRING_APPLICATION_JSON: '{"management":{"metrics":{"distribution":{"percentiles-histogram":{"http.server.requests":true}}}}}'
"@ | Set-Content -LiteralPath $override -Encoding utf8
docker compose --project-directory $repo -f (Join-Path $repo 'docker-compose.yml') -f $override up -d --no-deps --no-build --wait --wait-timeout 120 ticketing-service
if ($LASTEXITCODE -ne 0) { throw 'Observed Ticketing did not become healthy' }
[ordered]@{ baseRevision = $revision; workingTree = $true; sourceSha256 = $hash; image = $image; snapshot = $snapshot } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $snapshot 'build.json') -Encoding utf8
Write-Host "Observed source snapshot: $snapshot"
