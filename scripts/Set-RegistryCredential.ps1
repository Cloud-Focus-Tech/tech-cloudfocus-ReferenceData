param(
    [Parameter(Mandatory)][string]$Username,
    [Security.SecureString]$Token,
    [string]$ExpectedContext = 'admin@cloudfocus-lab'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
if ((kubectl config current-context) -ne $ExpectedContext) { throw "Select Kubernetes context $ExpectedContext first." }
if ($null -eq $Token) { $Token = Read-Host 'GHCR token with read:packages access' -AsSecureString }
$plain = [Net.NetworkCredential]::new('', $Token).Password
$auth = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:$plain"))
$plain = $null
$config = @{ auths = @{ 'ghcr.io' = @{ auth = $auth } } } | ConvertTo-Json -Depth 10 -Compress
@{
    apiVersion = 'v1'; kind = 'Secret'; type = 'kubernetes.io/dockerconfigjson'
    metadata = @{ name = 'ghcr-pull'; namespace = 'reference-data-2' }
    data = @{ '.dockerconfigjson' = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($config)) }
} | ConvertTo-Json -Depth 10 -Compress | kubectl apply -f -
$auth = $config = $Token = $null
