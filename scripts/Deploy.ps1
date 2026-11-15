[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Image,
    [string]$ExpectedContext = 'admin@cloudfocus-lab'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
if ($Image -notmatch '^ghcr\.io/cloudfocused/tech-cloudfocus-referencedata@sha256:[a-f0-9]{64}$') {
    throw 'Image must be an immutable digest in ghcr.io/cloudfocused/tech-cloudfocus-referencedata.'
}
if ((kubectl config current-context) -ne $ExpectedContext) { throw "Select Kubernetes context $ExpectedContext first." }
$root = Split-Path $PSScriptRoot -Parent
kubectl get namespace reference-data-2 -o name | Out-Null
kubectl get secret azure-blob-credentials ghcr-pull -n reference-data-2 -o name | Out-Null
$manifest = Get-Content (Join-Path $root 'deploy/kubernetes/deployment.yaml') -Raw
$manifest = $manifest.Replace('ghcr.io/cloudfocused/tech-cloudfocus-referencedata:unconfigured', $Image)
kubectl apply -f (Join-Path $root 'deploy/kubernetes/configmap.yaml')
kubectl apply -f (Join-Path $root 'deploy/kubernetes/service.yaml')
$manifest | kubectl apply -f -
kubectl apply -f (Join-Path $root 'deploy/kubernetes/httproute.yaml')
kubectl rollout status deployment/reference-data -n reference-data-2 --timeout=600s
kubectl wait httproute/reference-data -n reference-data-2 '--for=jsonpath={.status.parents[0].conditions[?(@.type=="Accepted")].status}=True' --timeout=120s
$response = Invoke-RestMethod 'https://api.lab.cloudfocus.tech/rds/health/ready' -TimeoutSec 30
if ($response.status -ne 'UP') { throw 'The public readiness check failed.' }
Write-Output "Deployed $Image to https://api.lab.cloudfocus.tech/rds"
