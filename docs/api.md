# Current HTTP API

Source: [controller/collections.java](../src/main/java/tech/cloudfocus/ReferenceData/controller/collections.java). All mappings are GET routes under the controller's `/api` prefix. The examples below describe source behavior; they were not verified against a running server during this review.

| Route | Successful response | Implementation |
| --- | --- | --- |
| `/api/health/live` | `200`, `{"status":"UP"}` | Constant map |
| `/api/health/ready` | `200`, `{"status":"UP"}` | Constant map |
| `/api/collections` | `200`, array of four collection summaries | Controller -> service -> repository |
| `/api/collection/{id}` | `200`, message containing the ID | Placeholder map |
| `/api/collection/{id}/items` | `200`, message containing the ID | Placeholder map |

Responses are serialized as JSON through Spring MVC. JSON object member order is not part of the contract. There are no declared request bodies, query parameters, paging controls, filters, or version selectors.

## Collection list

`GET /api/collections` returns the following entries in this order:

```json
[
  {"name":"currencies","kind":"set","version":"v1","count":3},
  {"name":"languages","kind":"keyValue","version":"v1","count":7927},
  {"name":"timezones","kind":"keyValue","version":"v1","count":597},
  {"name":"countries","kind":"keyValue","version":"v1","count":249}
]
```

These values are literals in [CollectionRepository.findAll()](../src/main/java/tech/cloudfocus/ReferenceData/repository/CollectionRepository.java). Counts happen to match those four dataset files at review time, but the application does not calculate them. Editing a JSON file or the index will not change this response.

| Field | Java type | Current meaning |
| --- | --- | --- |
| `name` | `String` | Collection name |
| `kind` | `String` | Literal `set` or `keyValue`; not an enum |
| `version` | `String` | Literal `v1`; no runtime version lookup |
| `count` | `int` | Hard-coded number of items |

## Placeholder detail and items

`GET /api/collection/currencies`:

```json
{"message":"Details of collection currencies"}
```

`GET /api/collection/currencies/items`:

```json
{"message":"Items of collection currencies"}
```

An arbitrary ID such as `does-not-exist` also produces the corresponding message with status 200 when it matches the route. There is no lookup, dataset serialization, custom 404 behavior, or invalid-ID validation in these handlers. The collection ID's case is simply reflected in the message.

## Health and error semantics

Both health routes return the same constant `UP` value. Readiness does not establish that data files exist or can be read. These are application-controller endpoints, not Actuator endpoints.

There are no custom exception handlers or error DTOs. Requests outside these mappings and framework-level errors use framework behavior; no stable application-specific error schema is defined. The controller's documentation comments mention a 500 response but do not implement an error policy.

No application authentication or role checks are implemented. `ReferenceData.Reader` appears only in the unused catalog metadata; it is not an access requirement enforced by these routes.

## Manual checks after starting the application

PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/api/health/live
Invoke-RestMethod http://localhost:8080/api/health/ready
Invoke-RestMethod http://localhost:8080/api/collections
Invoke-RestMethod http://localhost:8080/api/collection/currencies
Invoke-RestMethod http://localhost:8080/api/collection/currencies/items
```

Use these as manual smoke checks, not as a substitute for endpoint tests when changing API behavior.
