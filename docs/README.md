# ReferenceData: agent starting point

Updated 2026-11-14. ReferenceData is a Java 25 / Spring Boot 4.1.2 REST service that loads versioned JSON collections from Azure Blob Storage into memory.

Read [architecture](architecture.md), [API](api.md), [data catalog](data-catalog.md), [development setup](development.md), and [native packaging/deployment](deployment.md).

For Backstage, use the root [catalog-info.yaml](../catalog-info.yaml) and [registration instructions](backstage.md). After deploying this change, the descriptor is hosted at `/rds/catalog-info.yaml` and registers the service plus its OpenAPI API entity.

## Current behavior

- `StorageProvider` is the provider-neutral boundary; `AzureBlobStorageProvider` is the single production adapter.
- The configured container (default `reference-data`) has `index.json` at its root. Its `blob` paths identify the dataset files relative to that root.
- Startup validates the entire catalog and loads enabled datasets. `ENABLED_COLLECTIONS=*` (the default) loads all, a comma-separated list loads chosen index names, and empty enables none. Missing configuration, unavailable enabled files, malformed loaded JSON, or invalid catalog/data shapes fail startup.
- The repository publishes complete in-memory snapshots. Requests do not access Azure.
- Polling starts after the first refresh interval (default 300000 milliseconds / five minutes) and repeats with a fixed delay after each check completes.
- Every poll checks the index and enabled blobs, including when the index is unchanged. Unchanged ETags reuse cached JSON. Changed blobs are downloaded with `If-Match`.
- Failed refreshes retain the last complete snapshot, log a sanitized error, and retry next interval. Startup failure is fatal; background refresh failure is recoverable.
- `/collections` lists every index entry with `enabled`; disabled entries have `count: null`. Unknown and disabled detail/items IDs return 404. Readiness requires a loaded snapshot and remains ready while serving the last good snapshot, including when no datasets are enabled.
- No application authentication/authorization is implemented. `allowedRoles` is retained in the cached catalog but is not enforced.
- Springdoc provides Swagger UI at `/api/swagger-ui.html` and OpenAPI JSON/YAML under `/api/v3/api-docs`. The external equivalents use `/rds` after publishing/deploying this change; see [API documentation](api.md#openapi-and-swagger-ui).

## Source of truth

Start with [StorageProvider](../src/main/java/tech/cloudfocus/ReferenceData/storage/StorageProvider.java), [Azure adapter](../src/main/java/tech/cloudfocus/ReferenceData/storage/AzureBlobStorageProvider.java), [repository/loader](../src/main/java/tech/cloudfocus/ReferenceData/repository/CollectionRepository.java), [controller](../src/main/java/tech/cloudfocus/ReferenceData/controller/collections.java), and [properties](../src/main/resources/application.properties).

`data/` contains local reference fixtures: 28 datasets, seven indexed collections. Production reads the cloud catalog, not these local files. `countries` maps to `address-countries/v1.json` in the sample index. The old four-entry hard-coded list has been removed.

Local secrets belong in the ignored `.env`; only the blank [.env.example](../.env.example) is tracked. Existing `target/` builds and generated `HELP.md` are ignored and may be stale. Keep these notes current when behavior changes.
