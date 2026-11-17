# Native package and lab Kubernetes deployment

Prepared 2026-11-14 for `admin@cloudfocus-lab`, Linux amd64 Talos nodes, and namespace **reference-data-2**.

The external base URL is **https://api.lab.cloudfocus.tech/rds**. The existing Cilium `default/lab-gateway` owns `10.10.30.81`; DNS resolves to that address. This cluster uses Gateway API rather than an Ingress controller.

## Manual deployment: start here

The first manual rollout completed on 2026-11-15: both replicas in `reference-data-2` are ready and public HTTPS readiness passes. The namespace, Secrets, Service and gateway routes are already configured. A GitHub Actions runner is not required for manual deployment.

From the repository root, publish your image to GHCR first, then run:

```powershell
./scripts/Deploy.ps1 -Tag latest
```

This resolves the tag to an immutable digest, applies the namespaced resources, waits for both replicas, and verifies the public endpoint. Running `./scripts/Deploy.ps1` without arguments also uses `latest`. To deploy a specific build, use `-Tag sha-YOUR_COMMIT_SHA`, or use `-Image` with a full digest as shown below. Tag resolution needs the Docker CLI with Buildx and GHCR read access (use `docker login ghcr.io` if needed); the Docker engine does not build anything during deployment. The existing workflow's digest-based `-Image` mode requires only PowerShell and kubectl.

The script replaces the service image whether the manifest contains a tag or digest, requires exactly one matching service image, and verifies browser GET and preflight CORS headers for the configured origins after rollout. Avoid applying a mutable `:latest` image directly: an existing `IfNotPresent` pull policy can reuse an older cached image.

Verify the result:

```powershell
kubectl get pods -n reference-data-2
Invoke-RestMethod https://api.lab.cloudfocus.tech/rds/health/ready
Invoke-RestMethod https://api.lab.cloudfocus.tech/rds/collections
```

## Select collections at startup

The ConfigMap defaults `ENABLED_COLLECTIONS` to `"*"`, preserving all currently indexed collections. To load a subset, set that value to comma-separated index names, for example `"address-countries,currencies"`. Use an explicitly empty string to enable none. Names are case-sensitive and must exist in the index. `/collections` still lists every entry with `enabled`; disabled detail/items routes return 404 and disabled counts are null.

Publish the updated image before deploying this feature. After changing selection, apply the ConfigMap and recreate pods so they read the new environment. If `Deploy.ps1` selects the same image digest as the current Deployment, its ConfigMap apply alone does not restart pods:

```powershell
kubectl apply -f deploy/kubernetes/configmap.yaml
kubectl rollout restart deployment/reference-data -n reference-data-2
kubectl rollout status deployment/reference-data -n reference-data-2 --timeout=600s
Invoke-RestMethod https://api.lab.cloudfocus.tech/rds/collections
```

No image rebuild is needed for subsequent selection changes. See [collection selection](api.md#collection-selection) for validation and refresh behavior. The native smoke test runs the same image with all, one, and zero collections enabled.

## Build and release flow

[The workflow](../.github/workflows/native-package.yml) runs on pull requests and pushes to `master`, and supports manual dispatch. It builds a Linux amd64 native image on `ubuntu-24.04`. The Docker build first runs the JVM tests, then Spring AOT processing and GraalVM native compilation. `-march=compatibility` avoids requiring the build machine's CPU instruction set on cluster nodes.

The runtime image is distroless Debian 13 `cc`, runs as UID/GID 65532, and contains the native executable without a JVM or shell. Only build-stage fixtures are copied into the compiler stage. The allowlisted `.dockerignore` excludes `.env`, Git history, Kubernetes credentials, and local build outputs.

A smoke test runs the actual native container with a local read-only Blob HTTP fixture and fake credentials. It exercises startup, the Azure SDK, JSON parsing/serialization, readiness, and collection/item endpoints. Real Azure credentials are not available to the build job. See [GraalVM containers](https://github.com/graalvm/container) and [Spring native-image documentation](https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html).

After smoke tests pass on `master`, the workflow publishes `ghcr.io/cloud-focus-tech/tech-cloudfocus-referencedata` with `sha-<commit>` and `latest` tags using `GITHUB_TOKEN` and `packages: write`. No registry password is needed in repository secrets. New GHCR packages default to private; confirm private visibility in the package settings, and ensure organization policy allows Actions to create packages. The image source label links the package to this repository. See [GitHub's publishing guide](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images).

Deployment runs only for `master`, never for pull requests. It requires a **Windows x64 self-hosted runner with label `reference-data-2`** and uses the published immutable image digest. The `lab` GitHub environment can hold deployment protection rules. Manual dispatch with `deploy=false` publishes without deploying. Only one deployment runs at a time.

## Cluster bootstrap: already applied

The setup created:

- Namespace `reference-data-2`, the configuration ConfigMap, and Service.
- Secret `azure-blob-credentials` containing only `STORAGE_CONNECTION_STRING` from the local `.env`.
- Secret `ghcr-pull`, copied from the existing `reference-data` namespace without printing its contents.
- Certificate `default/api-lab-tls` issued by the existing `cloudfocus-kubernetes-ca` ClusterIssuer.
- `http-api` and `https-api` listeners appended to `default/lab-gateway`, preserving other listeners. Route attachment is restricted to this namespace.
- HTTPS `/rds` routing and an HTTP-to-HTTPS redirect, both attached to the shared Gateway.

`scripts/Initialize-Lab.ps1` reproduces the bootstrap, using the current context only after checking its name. The Gateway patch tests its resource version to avoid overwriting concurrent changes. It appends missing listeners rather than replacing the shared Gateway. A later listener with a conflicting hostname/port is rejected.

```powershell
./scripts/Initialize-Lab.ps1
```

The copied registry credential must be authorized to pull this new private package. If it lacks access, create a classic GitHub PAT with `read:packages`, using an account that has access to the package and any required organization SSO authorization. Update the Secret using a masked prompt; do not paste the token into a command or commit it:

```powershell
./scripts/Set-RegistryCredential.ps1 -Username YOUR_GITHUB_USERNAME
```

The certificate is issued by the lab CA, not a public issuer. The runner and API clients must trust that CA. The deployment's HTTPS verification deliberately keeps certificate validation enabled.

## Register the lab runner

A runner has not been registered automatically: registration needs a short-lived token from the repository's GitHub settings. Runner registration is needed only for automatic Actions deployments; the package is now published and the first manual deployment is running.

1. Open [repository runner settings](https://github.com/CloudFocused/tech-cloudfocus-ReferenceData/settings/actions/runners/new). Select Windows x64 and follow GitHub's download/checksum/extraction commands for the latest runner. Use a separate directory such as `D:\actions-runner\reference-data-2`, outside this repository.
2. In that directory, use GitHub's one-time registration token with the following configuration. Enter the actual token only on your machine; do not store it in this project:

   ```powershell
   ./config.cmd --url https://github.com/CloudFocused/tech-cloudfocus-ReferenceData --token ONE_TIME_REGISTRATION_TOKEN --labels reference-data-2 --name lab-reference-data-2
   ```

3. The runner needs Git, PowerShell 7 (`pwsh`), and `kubectl` on PATH. Run it under your Windows account so it can use the existing default context. Check `kubectl config current-context` returns `admin@cloudfocus-lab`, then start `./run.cmd`. A Windows service can also be used, but its service account must have its own accessible kubeconfig and lab CA trust; it does not automatically inherit your user profile.
4. In repository settings, create the `lab` environment if desired and confirm Actions may write packages. After the first publish, confirm the GHCR package stays private and the pull credential can read it.
5. Commit and push these project changes to `master`, or dispatch **Native package and lab deployment** from the Actions tab after the workflow is present on GitHub. Builds run in GitHub; the local runner needs no Docker or GraalVM installation. If the runner is offline, the deployment job queues until it is available.

GitHub cannot register or run a local runner using only this machine's Kubernetes context. GitHub's [self-hosted runner setup guide](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners) describes the registration lifecycle.

## Deployment behavior

[Deploy.ps1](../scripts/Deploy.ps1) checks the expected context and existing Secrets, applies only namespaced application resources, inserts a GHCR digest into the Deployment, waits for rollout and route acceptance, then checks the public HTTPS readiness URL. It does not modify the shared Gateway, create credentials, or require Azure credentials in GitHub secrets.

The Deployment uses two replicas, rolling updates with zero unavailable replicas, startup/readiness/liveness probes, CPU/memory limits, a read-only root filesystem and writable `/tmp`. Each replica independently loads and refreshes its in-memory catalog. Failed initial loads cause the new pod to fail; old ready pods remain during an unsuccessful rollout. Failed later refreshes retain each pod's last valid data.

For a manual release, use the published digest:

```powershell
./scripts/Deploy.ps1 -Image 'ghcr.io/cloud-focus-tech/tech-cloudfocus-referencedata@sha256:REPLACE_WITH_64_HEX_DIGEST'
```

Do not apply the entire `deploy/kubernetes` directory directly: its Deployment file intentionally contains an `:unconfigured` image placeholder. Use `Deploy.ps1` to supply the exact published digest.

Routes use Gateway API [prefix rewriting](https://gateway-api.sigs.k8s.io/guides/user-guides/http-redirect-rewrite/): `/rds` becomes the application's `/api` prefix. Examples:

| External URL | Application route |
| --- | --- |
| `https://api.lab.cloudfocus.tech/rds/collections` | `/api/collections` |
| `https://api.lab.cloudfocus.tech/rds/collection/currencies/items` | `/api/collection/currencies/items` |
| `https://api.lab.cloudfocus.tech/rds/health/ready` | `/api/health/ready` |
| `https://api.lab.cloudfocus.tech/rds/swagger-ui/index.html` | `/api/swagger-ui/index.html` |
| `https://api.lab.cloudfocus.tech/rds/v3/api-docs` | `/api/v3/api-docs` |

Springdoc requires publishing the updated image and deploying it using `Deploy.ps1`, which also applies the updated route manifest. The gateway handles `/rds/swagger-ui.html` with a 302 redirect to `/rds/swagger-ui/index.html`. All Swagger assets use the existing prefix rewrite; the configuration and OpenAPI document use relative URLs so the UI does not escape to internal `/api` paths.

`/rds` is the API base, not an HTML home page; use the paths above. Other paths on this hostname are not claimed by these routes.

## Local verification and operations

```powershell
docker build -t reference-data-native:local .
./scripts/Smoke-Native.ps1 -Image reference-data-native:local
kubectl get pods,service,httproute -n reference-data-2
kubectl rollout status deployment/reference-data -n reference-data-2
Invoke-RestMethod https://api.lab.cloudfocus.tech/rds/health/ready
```

Use `kubectl describe pod -n reference-data-2 POD_NAME` for `ImagePullBackOff`; verify package visibility and `ghcr-pull` access. For startup failures inspect pod logs and Azure Secret/container settings. Rotate Azure credentials by rerunning bootstrap and restarting the Deployment. ConfigMap environment changes also require a rollout restart. To roll back a deployed revision, use `kubectl rollout undo deployment/reference-data -n reference-data-2` and wait for rollout; the next workflow run reapplies the selected digest.

Validation on 2026-11-14: all nine JVM tests passed inside the Docker build; portable GraalVM native compilation succeeded; the final native smoke test loaded seven fixture collections; a separate native-container check loaded three collections from the current Azure catalog over HTTPS and reported startup in 1.12 seconds. This is one local measurement including Azure loading, not a latency guarantee. Docker reports the uncompressed local image size as 274,126,844 bytes (about 261 MiB). Workflow actionlint and PowerShell/Python syntax checks passed. Both HTTPRoutes are accepted; HTTP redirects to HTTPS, and the new hostname's HTTPS certificate is trusted by this machine. At that stage the backend returned 503 because no Deployment had been started.

Update 2026-11-15: the published image in `ghcr.io/cloud-focus-tech/tech-cloudfocus-referencedata` was manually deployed at digest `sha256:2c4e5f1960c13fd255d1f49c0ba3847167b2cfef56861c498e2f8ff8cbf8f7f4`. Both replicas became ready, the private-image pull Secret worked, and public HTTPS readiness passed. The earlier 503 status was resolved. No repository commit or push was performed during this manual deployment.

Update 2026-11-16: Backstage browser requests were still reaching cached image `sha256:ea9318f4f9c51701a54e799f14a710f5e15a9a4c6d9391b9e3afc014513efa6a` through pods configured with `:latest` and `IfNotPresent`. After fixing digest substitution in `Deploy.ps1`, published image `sha256:8ab8ca13f46e67aed96feba211b85e922f1014d64414f3704e746cba465fc6c1` (source revision `858c054efc50e99493137c84630aa20153c756b9`) passed the native smoke test including CORS and was deployed. All three replicas became ready on the pinned digest. Public readiness and collection GET responses returned 200 with `Access-Control-Allow-Origin: https://developer.lab.cloudfocus.tech`; OPTIONS also returned 200 and allowed `GET,HEAD`.
