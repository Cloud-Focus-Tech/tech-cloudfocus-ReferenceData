# Current HTTP API

Updated 2026-11-14. All routes use the `/api` prefix. Responses come from the in-memory snapshot, with no per-request Azure calls.

| GET route | Behavior |
| --- | --- |
| `/api/health/live` | 200 with `{"status":"UP"}` |
| `/api/health/ready` | 200 with `{"status":"UP"}` once a snapshot is loaded; otherwise 503 |
| `/api/collections` | Array of collection summaries in index order |
| `/api/collection/{id}` | One collection summary; 404 for an unknown name |
| `/api/collection/{id}/items` | The dataset JSON object or string array; 404 for an unknown name |

Names are case-sensitive. The list uses plural `collections`; detail and items use singular `collection`. There are no query filters, paging, version selection, or request bodies.

Example detail for `/api/collection/currencies` with the sample catalog:

```json
{"name":"currencies","kind":"set","version":"v1","count":3}
```

Items for the same collection:

```json
["USD","CAD","EUR"]
```

Key/value collections preserve the keyed-object structure, for example:

```json
{"AK":{"name":"Alaska"}}
```

`count` is computed from the actual loaded dataset. The cloud index determines list membership, order, kind and version. The local sample index would produce seven collection summaries.

Startup fails if initial loading fails. Failed periodic refreshes retain the previous complete snapshot, including its collection list and counts; readiness remains UP while serving that snapshot. Unknown IDs use Spring `ResponseStatusException`; no custom stable error body is defined. No application authentication or role enforcement exists, including for catalog `allowedRoles`.

## Manual checks after starting the application

```powershell
Invoke-RestMethod http://localhost:8080/api/health/ready
Invoke-RestMethod http://localhost:8080/api/collections
Invoke-RestMethod http://localhost:8080/api/collection/currencies
Invoke-RestMethod http://localhost:8080/api/collection/currencies/items
```
