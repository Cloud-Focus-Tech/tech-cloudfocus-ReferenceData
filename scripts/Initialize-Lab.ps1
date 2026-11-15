[CmdletBinding()]
param(
    [string]$ExpectedContext = 'admin@cloudfocus-lab',
    [string]$EnvFile = (Join-Path $PSScriptRoot '../.env'),
    [string]$RegistrySecretSourceNamespace = 'reference-data'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path $PSScriptRoot -Parent
if ((kubectl config current-context) -ne $ExpectedContext) { throw "Select Kubernetes context $ExpectedContext first." }

# Read only the required value; never put the full .env file in a cluster Secret.
$connection = [Environment]::GetEnvironmentVariable('STORAGE_CONNECTION_STRING')
if ([string]::IsNullOrWhiteSpace($connection) -and (Test-Path -LiteralPath $EnvFile)) {
    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        if ($line -match '^\s*STORAGE_CONNECTION_STRING\s*=(.*)$') { $connection = $Matches[1].Trim() }
    }
}
if ([string]::IsNullOrWhiteSpace($connection)) { throw 'Set STORAGE_CONNECTION_STRING in .env or the environment.' }
if ($connection.StartsWith('"') -or $connection.StartsWith("'")) { throw 'Remove surrounding quotes from the connection string in .env.' }

kubectl apply -f (Join-Path $root 'deploy/kubernetes/namespace.yaml')
$secret = @{
    apiVersion = 'v1'; kind = 'Secret'; type = 'Opaque'
    metadata = @{ name = 'azure-blob-credentials'; namespace = 'reference-data-2' }
    data = @{ STORAGE_CONNECTION_STRING = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($connection)) }
}
$secret | ConvertTo-Json -Depth 10 -Compress | kubectl apply -f -
$connection = $null
$secret = $null

# Reuse the lab's existing registry credential without printing or writing its value.
$registrySecret = kubectl get secret ghcr-pull -n $RegistrySecretSourceNamespace -o json | ConvertFrom-Json
if ($registrySecret.type -ne 'kubernetes.io/dockerconfigjson') { throw 'The source ghcr-pull must be a Docker registry Secret.' }
@{
    apiVersion = 'v1'; kind = 'Secret'; type = $registrySecret.type
    metadata = @{ name = 'ghcr-pull'; namespace = 'reference-data-2' }
    data = $registrySecret.data
} | ConvertTo-Json -Depth 10 -Compress | kubectl apply -f -
$registrySecret = $null

kubectl apply -f (Join-Path $root 'deploy/bootstrap/certificate.yaml')
# Append only missing listeners, preserving all unrelated shared Gateway settings.
$gateway = kubectl get gateway lab-gateway -n default -o json | ConvertFrom-Json
$desiredListeners = Get-Content (Join-Path $root 'deploy/bootstrap/gateway-listeners.json') -Raw | ConvertFrom-Json
$operations = @(@{ op = 'test'; path = '/metadata/resourceVersion'; value = $gateway.metadata.resourceVersion })
foreach ($listener in $desiredListeners) {
    $existing = @($gateway.spec.listeners | Where-Object name -eq $listener.name)
    if ($existing.Count -gt 0) {
        if ($existing[0].hostname -ne $listener.hostname -or $existing[0].port -ne $listener.port) {
            throw "Existing Gateway listener $($listener.name) conflicts with the requested endpoint."
        }
        continue
    }
    $operations += @{ op = 'add'; path = '/spec/listeners/-'; value = $listener }
}
if ($operations.Count -gt 1) {
    $patchPath = Join-Path $root 'target/gateway-patch.json'
    New-Item -ItemType Directory -Force (Split-Path $patchPath -Parent) | Out-Null
    $operations | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $patchPath
    kubectl patch gateway lab-gateway -n default --type=json --patch-file $patchPath
}
kubectl wait -n default --for=condition=Ready certificate/api-lab-tls --timeout=120s
kubectl apply -f (Join-Path $root 'deploy/kubernetes/configmap.yaml')
kubectl apply -f (Join-Path $root 'deploy/kubernetes/service.yaml')
kubectl apply -f (Join-Path $root 'deploy/kubernetes/httproute.yaml')
Write-Output 'Lab bootstrap complete. The registry credential must have access to the new private GHCR package.'
