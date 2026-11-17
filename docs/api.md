# Current HTTP API

Updated 2026-11-16. All routes use the `/api` prefix. Collection responses come from the in-memory snapshot, with no per-request Azure calls.

## OpenAPI and Swagger UI

Springdoc 3.2.0 generates OpenAPI 3.1 documentation from the controllers. Local URLs:

- Swagger UI: `http://localhost:8080/api/swagger-ui.html` (or `/api/swagger-ui/index.html` directly).
- OpenAPI JSON: `http://localhost:8080/api/v3/api-docs`.
- OpenAPI YAML: `http://localhost:8080/api/v3/api-docs.yaml`.

After publishing and deploying the Springdoc image and updated route manifest:

- Swagger UI: `https://api.lab.cloudfocus.tech/rds/swagger-ui.html`.
- OpenAPI JSON: `https://api.lab.cloudfocus.tech/rds/v3/api-docs`.
- OpenAPI YAML: `https://api.lab.cloudfocus.tech/rds/v3/api-docs.yaml`.

The gateway redirects the UI welcome URL to `/rds/swagger-ui/index.html`; all assets and documentation endpoints remain under its `/rds` route. Swagger configuration/spec URLs are relative. `OpenApiConfiguration` describes a relative server (`..`, resolved against the spec URL) and removes the internal `/api` prefix from documented operation paths. This allows the same document and "Try it out" requests to work locally under `/api` and externally under `/rds` without hardcoding a hostname. See [Springdoc setup](https://springdoc.org/getting-started.html) and [OpenAPI relative server URLs](https://swagger.io/docs/specification/v3_0/api-host-and-base-path/#relative-urls).

These endpoints do not add authentication; they document the existing public GET handlers. Responses containing dataset-specific item properties retain their dynamic JSON shape rather than a fixed per-dataset schema.

## Backstage catalog

Backstage ingestion also has `GET /api/catalog-info.yaml` (the canonical root catalog descriptor) and `GET /api/backstage/openapi.json` (the generated definition with an absolute public server URL). External URLs use `/rds` instead of `/api`. See [Backstage registration](backstage.md). These ingestion endpoints are not included among the documented business API operations.

## Collection and health endpoints

| GET route | Behavior |
| --- | --- |
| `/api/health/live` | 200 with `{"status":"UP"}` |
| `/api/health/ready` | 200 with `{"status":"UP"}` once a snapshot is loaded; otherwise 503 |
| `/api/collections` | All indexed collection summaries in index order, including `enabled` |
| `/api/collection/{id}` | One enabled collection summary; 404 for an unknown or disabled name |
| `/api/collection/{id}/items` | The enabled dataset JSON object or string array; 404 for an unknown or disabled name |

Names are case-sensitive. The list uses plural `collections`; detail and items use singular `collection`. There are no query filters, paging, version selection, or request bodies.

Example detail for `/api/collection/currencies` with the sample catalog:

```json
{"name":"currencies","kind":"set","version":"v1","count":3,"enabled":true}
```

Items for the same collection:

```json
["USD","CAD","EUR"]
```

Key/value collections preserve the keyed-object structure, for example:

```json
{"AK":{"name":"Alaska"}}
```

`count` is computed from the actual loaded dataset. Disabled collections have `count: null` because their data is not loaded. The cloud index determines list membership, order, kind and version. The local sample index produces 28 collection summaries regardless of how many are enabled.

## Collection selection

Set an environment variable before starting the service:

```properties
ENABLED_COLLECTIONS=address-countries,currencies
```

Selection uses the index's case-sensitive `name`, not its blob directory. Whitespace around names is ignored, repeated names are accepted, and index order is preserved. An unset variable defaults to `*` (enable all, including future index additions). An explicitly empty value enables none; the service still loads the index, lists its metadata, and becomes ready. A comma-separated list enables only the specified names. Mixing `*` with names, empty list entries, and names absent from the index are configuration errors that fail startup.

With the example selection, `/api/collections` includes entries like:

```json
[
  {"name":"address-countries","kind":"keyValue","version":"v1","count":249,"enabled":true},
  {"name":"timezones","kind":"keyValue","version":"v1","count":null,"enabled":false},
  {"name":"currencies","kind":"set","version":"v1","count":3,"enabled":true},
  {"name":"languages","kind":"keyValue","version":"v1","count":null,"enabled":false}
]
```

The sample index also returns the other 24 entries as disabled, including all three workflow datasets. Disabled data blobs are never downloaded, checked for ETag changes, or validated. Metadata for every index entry is still validated. Requests for disabled details or items return 404, just like unknown names.

Restart after changing selection. No configuration mutation endpoint or live environment reload is provided. On refresh, new unselected entries appear as disabled. If a specifically configured name disappears from the index, refresh fails and retains the last complete snapshot, following the existing refresh policy; update selection and restart to intentionally remove it.

Startup fails if initial loading fails. Failed periodic refreshes retain the previous complete snapshot, including its collection list and counts; readiness remains UP while serving that snapshot. Unknown IDs use Spring `ResponseStatusException`; no custom stable error body is defined. No application authentication or role enforcement exists, including for catalog `allowedRoles`.

## Manual checks after starting the application

```powershell
Invoke-RestMethod http://localhost:8080/api/health/ready
Invoke-RestMethod http://localhost:8080/api/collections
Invoke-RestMethod http://localhost:8080/api/collection/currencies
Invoke-RestMethod http://localhost:8080/api/collection/currencies/items
```
