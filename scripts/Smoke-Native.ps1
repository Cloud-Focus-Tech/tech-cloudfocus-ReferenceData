param([string]$Image = 'reference-data-native:local')
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path $PSScriptRoot -Parent
$id = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$network = "rds-smoke-$id"
$fixture = "rds-fixture-$id"
$app = "rds-app-$id"
$networkCreated = $fixtureCreated = $appCreated = $false
try {
    docker network create $network | Out-Null
    $networkCreated = $true
    docker run -d --name $fixture --network $network --network-alias blob-fixture `
        --mount "type=bind,source=$root/data,target=/data,readonly" `
        --mount "type=bind,source=$root/scripts/blob-fixture.py,target=/fixture.py,readonly" `
        python:3.14-alpine python /fixture.py | Out-Null
    $fixtureCreated = $true
    # The fixture starts before any application storage requests are allowed.
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $PSNativeCommandUseErrorActionPreference = $false
        docker exec $fixture python -c "import urllib.request; urllib.request.urlopen('http://localhost:10000/health')" 2>$null | Out-Null
        $ready = $LASTEXITCODE -eq 0
        $PSNativeCommandUseErrorActionPreference = $true
        if ($ready) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Blob fixture failed to start.' }
    # Deliberately fake credentials. No real Azure credentials enter this test.
    $connection = 'DefaultEndpointsProtocol=http;AccountName=testaccount;AccountKey=MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=;BlobEndpoint=http://blob-fixture:10000/testaccount;'
    docker run -d --name $app --network $network --publish '127.0.0.1::8080' `
        --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges `
        -e "STORAGE_CONNECTION_STRING=$connection" -e STORAGE_CONTAINER_NAME=reference-data `
        -e STORAGE_REFRESH_INTERVAL_MS=1000 $Image | Out-Null
    $appCreated = $true
    $container = docker inspect $app | ConvertFrom-Json
    $port = $container[0].NetworkSettings.Ports.'8080/tcp'[0].HostPort
    $base = "http://127.0.0.1:$port/api"
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $state = docker inspect --format '{{.State.Running}}' $app
        if ($state -ne 'true') { throw 'Native application exited before becoming ready.' }
        try {
            $health = Invoke-RestMethod "$base/health/ready" -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Native application did not become ready.' }
    $catalog = Get-Content (Join-Path $root 'data/index.json') -Raw | ConvertFrom-Json
    $collections = Invoke-RestMethod "$base/collections"
    if ($collections.Count -ne $catalog.collections.Count) { throw 'Native collection list does not match the catalog.' }
    $items = Invoke-RestMethod "$base/collection/currencies/items"
    if (($items -join ',') -ne 'USD,CAD,EUR') { throw 'Native JSON serialization failed.' }
    Start-Sleep -Seconds 2
    $detail = Invoke-RestMethod "$base/collection/currencies"
    if ($detail.count -ne 3) { throw 'Native collection detail/refresh check failed.' }
    Write-Output "Native smoke test passed: $($collections.Count) collections, JSON responses and readiness."
} catch {
    if ($appCreated) { docker logs $app }
    if ($fixtureCreated) { docker logs $fixture }
    throw
} finally {
    if ($appCreated) { docker rm -f $app | Out-Null }
    if ($fixtureCreated) { docker rm -f $fixture | Out-Null }
    if ($networkCreated) { docker network rm $network | Out-Null }
}
