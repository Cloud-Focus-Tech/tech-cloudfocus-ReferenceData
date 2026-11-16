[CmdletBinding(DefaultParameterSetName = 'Tag')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Digest')][string]$Image,
    [Parameter(ParameterSetName = 'Tag')][string]$Tag = 'latest',
    [string]$ExpectedContext = 'admin@cloudfocus-lab'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
if ((kubectl config current-context) -ne $ExpectedContext) { throw "Select Kubernetes context $ExpectedContext first." }
if ($PSCmdlet.ParameterSetName -eq 'Tag') {
    if ($Tag -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$') { throw 'Invalid container image tag.' }
    $repository = 'ghcr.io/cloud-focus-tech/tech-cloudfocus-referencedata'
    $digest = docker buildx imagetools inspect "${repository}:$Tag" --format '{{.Manifest.Digest}}'
    if ($digest -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Could not resolve the published image digest.' }
    $Image = "${repository}@$digest"
    Write-Output "Resolved tag '$Tag' to $Image"
}
if ($Image -notmatch '^ghcr\.io/cloud-focus-tech/tech-cloudfocus-referencedata@sha256:[a-f0-9]{64}$') {
    throw 'Image must be an immutable digest in ghcr.io/cloud-focus-tech/tech-cloudfocus-referencedata.'
}
$root = Split-Path $PSScriptRoot -Parent
kubectl get namespace reference-data-2 -o name | Out-Null
kubectl get secret azure-blob-credentials ghcr-pull -n reference-data-2 -o name | Out-Null
$manifest = Get-Content (Join-Path $root 'deploy/kubernetes/deployment.yaml') -Raw
# Match the service image even if the local manifest was changed to :latest.
$imagePattern = '(?m)^([ \t]*image:[ \t]*)ghcr\.io/cloud-focus-tech/tech-cloudfocus-referencedata(?::[A-Za-z0-9_.-]+|@sha256:[a-f0-9]{64})([ \t]*\r?)$'
$imageMatches = [regex]::Matches($manifest, $imagePattern)
if ($imageMatches.Count -ne 1) { throw 'Expected exactly one ReferenceData image in the deployment manifest.' }
$imageLine = $imageMatches[0]
$manifest = $manifest.Replace($imageLine.Value, $imageLine.Groups[1].Value + $Image + $imageLine.Groups[2].Value)
kubectl apply -f (Join-Path $root 'deploy/kubernetes/configmap.yaml')
kubectl apply -f (Join-Path $root 'deploy/kubernetes/service.yaml')
$manifest | kubectl apply -f -
kubectl apply -f (Join-Path $root 'deploy/kubernetes/httproute.yaml')
kubectl rollout status deployment/reference-data -n reference-data-2 --timeout=600s
kubectl wait httproute/reference-data -n reference-data-2 '--for=jsonpath={.status.parents[0].conditions[?(@.type=="Accepted")].status}=True' --timeout=120s
$response = Invoke-RestMethod 'https://api.lab.cloudfocus.tech/rds/health/ready' -TimeoutSec 30
if ($response.status -ne 'UP') { throw 'The public readiness check failed.' }
$origins = kubectl get configmap reference-data -n reference-data-2 -o 'jsonpath={.data.CORS_ALLOWED_ORIGINS}'
foreach ($origin in (([string]$origins -split ',').Trim() | Where-Object { $_ })) {
    $url = 'https://api.lab.cloudfocus.tech/rds/health/ready'
    $browserResponse = Invoke-WebRequest $url -Headers @{Origin=$origin} -TimeoutSec 30
    if ($browserResponse.Headers['Access-Control-Allow-Origin'] -ne $origin) {
        throw "Public GET response does not allow browser origin $origin."
    }
    $preflight = Invoke-WebRequest $url -Method Options -Headers @{
        Origin=$origin
        'Access-Control-Request-Method'='GET'
    } -TimeoutSec 30
    if ($preflight.Headers['Access-Control-Allow-Origin'] -ne $origin -or
        (([string]$preflight.Headers['Access-Control-Allow-Methods'] -split ',').Trim() -notcontains 'GET')) {
        throw "Public preflight response does not allow GET from $origin."
    }
}
Write-Output "Deployed $Image to https://api.lab.cloudfocus.tech/rds"
