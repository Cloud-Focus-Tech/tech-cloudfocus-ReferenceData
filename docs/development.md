# Development and verification

Updated 2026-11-14. The project requires Java 25; the POM pins Spring Boot 4.1.2. The wrapper points to Maven 3.10.0. Initial dependency resolution requires network access or a populated local cache.

## Local connection configuration

On a fresh checkout, copy `.env.example` to `.env` only if the local file does not already exist. Local settings are ignored by Git; retain placeholder values only in the tracked example.

```powershell
Copy-Item .env.example .env
```

Required/runtime settings:

| Key | Spring property | Default/use |
| --- | --- | --- |
| `STORAGE_CONNECTION_STRING` | `app.storage.connection-string` | Required Azure Blob Storage connection string |
| `STORAGE_CONTAINER_NAME` | `app.storage.container-name` | `reference-data` |
| `STORAGE_REFRESH_INTERVAL_MS` | `app.storage.refresh-interval-ms` | `300000` (five minutes); positive whole milliseconds |
| `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | Empty by default; comma-separated browser origins allowed to read `/api/**`. The lab ConfigMap allows `https://developer.lab.cloudfocus.tech`. |

The local `.env` has the nonsecret container/refresh defaults appended if missing; existing credentials are preserved. `STORAGE_ACCOUNT_NAME` and `STORAGE_ACCOUNT_KEY` are not used separately: authentication uses the connection string. Existing `CONNECTION_URL`, `CONNECTION_USERNAME`, and `CONNECTION_PASSWORD` mappings remain reserved for other integrations and are not used by this provider.

Upload `index.json` to the container root, then place its referenced JSON files at their exact `blob` paths. The application does not upload files or create containers. It requires read access for content and properties. Initial missing files, malformed data, credentials problems, or invalid configuration abort startup. There is no local-data fallback.

The `.env` import is `optional:file:./.env[.properties]`, resolved from the process working directory. Use `KEY=value`, without `export`, surrounding quotes or inline comments. This is Java-properties parsing, not a full shell dotenv parser: escape literal backslashes as `\\`; Spring `${...}` placeholders retain their normal meaning. Real environment variables override file values. A missing `.env` is allowed only if required settings are provided elsewhere. Restart after editing settings. File values are Spring properties, not additions to `System.getenv()`.

## Build, run and test

From the repository root:

```powershell
java -version
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The run command stays attached to the terminal; stop it before using the same terminal for packaging:

```powershell
.\mvnw.cmd package
java -jar target/ReferenceData-0.0.1-SNAPSHOT.jar
```

Use `./mvnw` on POSIX systems. An installed `mvn` can run the same goals. In the agent environment, dependency downloads were kept inside the ignored build directory with `mvn -Dmaven.repo.local=target/maven-repository test`; network-enabled execution was needed to populate that cache. See [manual API checks](api.md#manual-checks-after-starting-the-application).

Tests use an in-memory `StorageProvider` and mock Azure clients. The application-context test overrides the storage bean and disables `.env` import so tests do not require real credentials or contact Azure. Repository tests use local `data/` fixtures, cover initial loading and validation, unchanged-ETag reuse, independent blob changes, failed-refresh rollback/retry, catalog replacement, defensive copies, and changes during staging. API tests verify actual summaries/items and unknown-ID 404s. The adapter test verifies conditional `If-Match` downloads.

On 2026-11-14, `mvn -B -ntp -o -Dmaven.repo.local=target/maven-repository test` passed all nine tests with zero failures/errors/skips.

A separate read-only Azure check on the same date loaded all four collections listed in the actual cloud index from `reference-data` and completed a second ETag refresh check successfully. It used the local connection string without printing it and did not modify cloud data. The cloud index currently differs from the seven-entry local sample; this is supported because cloud catalog membership is authoritative.

GraalVM native compilation and the distroless runtime container have been verified on Linux amd64. The Docker build runs all 12 JVM tests, Spring AOT processing, and native compilation. The native smoke test loads seven local fixture collections and verifies readiness, collection summaries and JSON item responses without Azure credentials. A separate final native-container check loaded three collections from the current Azure catalog over HTTPS and reported startup in 1.12 seconds. Kubernetes manifests passed server-side dry-run validation; the namespace, Secrets, certificate, hostname-specific Gateway listeners and routes were bootstrapped in the lab. Both routes are accepted and HTTPS trust plus HTTP redirect were verified; the first manual rollout subsequently completed on 2026-11-15 with two ready replicas. Workflow validation uses actionlint with the custom runner label declared in `.github/actionlint.yaml`. See [deployment setup](deployment.md) for one-command manual deployment and optional runner registration for automatic deployments.

On 2026-11-15, Springdoc 3.2.0 was added and all 11 JVM tests passed, including generated paths/server URLs, OpenAPI JSON/YAML, and Swagger assets/configuration. The rebuilt GraalVM image also passed the native smoke test for collection loading, OpenAPI JSON/YAML and Swagger UI assets. The Gateway UI welcome redirect passed server-side dry-run validation. This source change was not published or redeployed during the Springdoc task; use the existing package/deployment workflow before accessing the new public documentation URLs.

Preserve unrelated working-tree changes, keep package case `tech.cloudfocus.ReferenceData`, and update these docs when changing contracts. `allowedRoles` is not an implemented security policy.


Backstage catalog support was verified on 2026-11-15: all 12 JVM tests passed, and the final native smoke test served the canonical root catalog-info.yaml, generated the Backstage definition with an absolute public server URL, and confirmed the normal OpenAPI definition still uses its relative server. The descriptor registers a Component and linked API; see [Backstage setup](backstage.md) for reader allowlisting and lab CA trust. This image was built locally but was not published or deployed as part of the catalog change.

On 2026-11-16, all 16 JVM tests passed after adding configurable CORS for Backstage browser requests. Coverage includes allowed origins, preflights, CORS headers on 404 responses, and rejection of unlisted origins and write preflights. The deployed API was confirmed to return 200 without CORS headers for the lab Backstage origin before this fix. The deployment image placeholder was restored to `:unconfigured` so `Deploy.ps1` can substitute the selected immutable digest. These changes have not been published or deployed; native compilation was not repeated for this change.
