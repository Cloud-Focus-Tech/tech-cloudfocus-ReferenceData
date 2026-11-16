param([string]$Image = 'reference-data-native:local')
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path $PSScriptRoot -Parent
$id = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$network = "rds-smoke-$id"
$fixture = "rds-fixture-$id"
$app = "rds-app-$id"
$origin = 'https://developer.lab.cloudfocus.tech'
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
    foreach ($selection in @('*', 'currencies', '')) {
        docker run -d --name $app --network $network --publish '127.0.0.1::8080' `
            --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges `
            -e "STORAGE_CONNECTION_STRING=$connection" -e STORAGE_CONTAINER_NAME=reference-data `
            -e STORAGE_REFRESH_INTERVAL_MS=1000 -e "CORS_ALLOWED_ORIGINS=$origin" `
            -e "ENABLED_COLLECTIONS=$selection" $Image | Out-Null
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
        $browserResponse = Invoke-WebRequest "$base/health/ready" -Headers @{Origin=$origin}
        if ($browserResponse.Headers['Access-Control-Allow-Origin'] -ne $origin) {
            throw 'Native API response is missing the configured CORS origin.'
        }
        $preflight = Invoke-WebRequest "$base/health/ready" -Method Options -Headers @{
            Origin=$origin
            'Access-Control-Request-Method'='GET'
        }
        if ($preflight.Headers['Access-Control-Allow-Origin'] -ne $origin -or
            (([string]$preflight.Headers['Access-Control-Allow-Methods'] -split ',').Trim() -notcontains 'GET')) {
            throw 'Native API does not allow the browser GET preflight.'
        }
        $catalog = Get-Content (Join-Path $root 'data/index.json') -Raw | ConvertFrom-Json
        $collections = Invoke-RestMethod "$base/collections"
        if ($collections.Count -ne $catalog.collections.Count) { throw 'Native collection list does not match the catalog.' }
        foreach ($collection in $collections) {
            $expectedEnabled = $selection -eq '*' -or $collection.name -eq $selection
            if ($collection.enabled -isnot [bool] -or $collection.enabled -ne $expectedEnabled) {
                throw "Native enabled flag is incorrect for $($collection.name)."
            }
            if (-not $expectedEnabled) {
                if ($collection.PSObject.Properties.Name -notcontains 'count' -or $null -ne $collection.count) {
                    throw 'Disabled collection count must be present and null.'
                }
                foreach ($suffix in @('', '/items')) {
                    $disabled = Invoke-WebRequest "$base/collection/$($collection.name)$suffix" -SkipHttpErrorCheck
                    if ([int]$disabled.StatusCode -ne 404) { throw 'Disabled collection endpoint must return 404.' }
                }
            }
        }
        if ($selection -ne '') {
            $items = Invoke-RestMethod "$base/collection/currencies/items"
            if (($items -join ',') -ne 'USD,CAD,EUR') { throw 'Native JSON serialization failed.' }
            Start-Sleep -Seconds 2
            $detail = Invoke-RestMethod "$base/collection/currencies"
            if ($detail.count -ne 3 -or -not $detail.enabled) { throw 'Native collection detail/refresh check failed.' }
        }
        $spec = Invoke-RestMethod "$base/v3/api-docs"
        if (-not $spec.paths.'/collections'.get -or $spec.servers[0].url -ne '..') {
            throw 'Native OpenAPI paths or relative server URL are incorrect.'
        }
        $summarySchema = $spec.components.schemas.Collection.properties
        if ($summarySchema.enabled.type -ne 'boolean' -or $summarySchema.count.type -notcontains 'null') {
            throw 'Native OpenAPI must describe the enabled flag and nullable count.'
        }
        $yaml = Invoke-WebRequest "$base/v3/api-docs.yaml"
        $yamlText = if ($yaml.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($yaml.Content) } else { [string]$yaml.Content }
        if (-not $yamlText.Contains('openapi:')) { throw 'Native OpenAPI YAML is unavailable.' }
        $swaggerConfig = Invoke-RestMethod "$base/v3/api-docs/swagger-config"
        if ($swaggerConfig.url -ne '../v3/api-docs') { throw 'Native Swagger spec URL must remain relative.' }
        $null = Invoke-WebRequest "$base/swagger-ui/index.html"
        $null = Invoke-WebRequest "$base/swagger-ui/swagger-ui-bundle.js"
        $initializer = Invoke-WebRequest "$base/swagger-ui/swagger-initializer.js"
        if (-not $initializer.Content.Contains('../v3/api-docs/swagger-config')) { throw 'Native Swagger config URL is incorrect.' }
        $catalogResponse = Invoke-WebRequest "$base/catalog-info.yaml"
        $catalogText = if ($catalogResponse.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($catalogResponse.Content) } else { [string]$catalogResponse.Content }
        if ($catalogText -ne [IO.File]::ReadAllText((Join-Path $root 'catalog-info.yaml'))) { throw 'Native catalog differs from the root catalog-info.yaml.' }
        $backstageSpec = Invoke-RestMethod "$base/backstage/openapi.json"
        if ($backstageSpec.servers[0].url -ne 'https://api.lab.cloudfocus.tech/rds' -or -not $backstageSpec.paths.'/collections'.get) {
            throw 'Native Backstage API definition is invalid.'
        }
        $spec = Invoke-RestMethod "$base/v3/api-docs"
        if ($spec.servers[0].url -ne '..') { throw 'Backstage rendering changed the normal OpenAPI definition.' }
        Write-Output "Native smoke test passed: selection '$selection', $($collections.Count) catalog entries, CORS, OpenAPI, Swagger UI and Backstage catalog."
        docker rm -f $app | Out-Null
        $appCreated = $false
    }
} catch {
    if ($appCreated) { docker logs $app }
    if ($fixtureCreated) { docker logs $fixture }
    throw
} finally {
    if ($appCreated) { docker rm -f $app | Out-Null }
    if ($fixtureCreated) { docker rm -f $fixture | Out-Null }
    if ($networkCreated) { docker network rm $network | Out-Null }
}
