# Reference-data files and catalog

All counts and shapes below were obtained by parsing the checked-in JSON on 2026-11-13; index coverage was updated on 2026-11-16. The directory contains **28 `v1.json` dataset files plus `index.json`**. These are local fixtures; production loads the same format from Azure, and tests load these files through an in-memory provider.

## Catalog format

[data/index.json](../data/index.json) contains a numeric `schemaVersion` of `1` and a `collections` array of 28 metadata records. For example:

```json
{
  "name": "address-countries",
  "kind": "keyValue",
  "version": "v1",
  "blob": "address-countries/v1.json",
  "allowedRoles": ["ReferenceData.Reader"],
  "hasSortOrder": false
}
```

| Field | Observed use |
| --- | --- |
| `name` | Logical collection name; can differ from the directory name |
| `kind` | `set` for currencies; `keyValue` for the other 27 indexed collections |
| `version` | `v1` for every entry |
| `blob` | Container-relative path in production; relative to `data/` for local fixtures |
| `allowedRoles` | Always `["ReferenceData.Reader"]`; unused by the application |
| `hasSortOrder` | True for the three workflow collections; false for the other 25 |

All 28 dataset files are indexed using their subfolder names as collection names. The first three existing entries remain `address-countries`, `address-type`, and `timezones`; the remaining entries follow in alphabetical path order. All referenced files exist. There is no `count` field in the index.

The Azure provider reads root `index.json` and follows each `blob` path. The loader validates schema version, unique names, supported kinds, paths, and payload shapes, then publishes a complete in-memory snapshot. Role metadata is not enforced. See [architecture](architecture.md) for ETag-based polling and refresh failure behavior.

## Dataset inventory

Each local dataset path is `data/<directory>/v1.json`. `Indexed` means it appears in the sample index; all 28 datasets would be available if that catalog is uploaded to the configured container. Production membership is determined by the cloud index. Counts are top-level object keys or array elements. All object datasets have a `name` field in their records; the last column lists additional observed fields.

| Directory | Entries | Shape | Indexed | Additional fields |
| --- | ---: | --- | --- | --- |
| `address-countries` | 249 | Object | Yes | None |
| `address-delivery-type` | 6 | Object | Yes | None |
| `address-directionals` | 8 | Object | Yes | None |
| `address-military-regions` | 3 | Object | Yes | None |
| `address-states` | 51 | Object | Yes | None |
| `address-street-suffix` | 202 | Object | Yes | `aliases` |
| `address-territory` | 5 | Object | Yes | None |
| `address-type` | 6 | Object | Yes | None |
| `address-unit` | 24 | Object | Yes | `requiresIdentifier` |
| `currencies` | 3 | Array | Yes | String elements |
| `demographic-education` | 10 | Object | Yes | None |
| `demographic-employment` | 10 | Object | Yes | None |
| `demographic-living` | 8 | Object | Yes | None |
| `demographic-marital-status` | 5 | Object | Yes | None |
| `demographic-race` | 7 | Object | Yes | None |
| `demographic-response-status` | 5 | Object | Yes | None |
| `languages` | 7927 | Object | Yes | None |
| `medical-claim-status` | 12 | Object | Yes | None |
| `medical-coding-pos` | 52 | Object | Yes | None |
| `medical-coding-service` | 44 | Object | Yes | None |
| `medical-coverage` | 8 | Object | Yes | None |
| `medical-plantypes` | 5 | Object | Yes | None |
| `medical-relationship` | 15 | Object | Yes | None |
| `medical-specialty` | 65 | Object | Yes | None |
| `timezones` | 597 | Object | Yes | `utcOffset`, `utcOffsetAsOf` |
| `workflow-doc-type` | 34 | Object | Yes | `category`, `description`, `sortOrder` |
| `workflow-priority` | 6 | Object | Yes | `description`, `level`, `sortOrder` |
| `workflow-status` | 17 | Object | Yes | `category`, `isTerminal`, `sortOrder` |

Every dataset is discoverable through the sample index. By default the loader includes all 28 indexed datasets, including the three workflow datasets. `ENABLED_COLLECTIONS` can select a subset by index name; all indexed names remain discoverable through `/collections` with their enabled status. No hard-coded collection list remains.

## Record shapes and handling details

The `set` file, [currencies/v1.json](../data/currencies/v1.json), is exactly:

```json
["USD", "CAD", "EUR"]
```

The other 27 files are JSON objects keyed by a string code or identifier. Values are objects rather than plain label strings. For example, `address-states` includes:

```json
{"AK":{"name":"Alaska"}}
```

Preserve string keys, including leading zeroes in medical codes such as `"01"`, and preserve Unicode text. Some identifiers contain slashes, as in timezone keys such as `Africa/Abidjan`. Item identifiers are not currently exposed as URL path parameters.

Additional field types observed in the data:

| Dataset | Field types and interpretation |
| --- | --- |
| `address-street-suffix` | `aliases`: array of strings for alternate spellings/abbreviations |
| `address-unit` | `requiresIdentifier`: boolean |
| `timezones` | `utcOffset`: signed offset string; `utcOffsetAsOf`: timestamp string |
| `workflow-doc-type` | `category`, `description`: strings; `sortOrder`: integer |
| `workflow-priority` | `description`: string; `level`, `sortOrder`: integers |
| `workflow-status` | `category`: string; `isTerminal`: boolean; `sortOrder`: integer |

Timezone offsets are stored snapshots with `utcOffsetAsOf` values of `2026-10-20T12:00:00Z`, rather than offsets calculated for an arbitrary request time. Future consumers should respect that timestamp.

Every entry in the three indexed workflow files has `sortOrder`. Sorting by it is a possible consumer behavior, not a runtime feature today. Workflow status categories and terminal flags are lookup metadata; there is no state-transition engine in this service.

## Publishing updates

Upload datasets under their catalog paths, and publish `index.json` at the container root. Prefer immutable versioned paths and publish the index last for coordinated releases. Only enabled indexed datasets are loaded. Polling checks both the index and each enabled file's ETag; unchanged files are not downloaded. Invalid index metadata or enabled initial data fails startup; invalid refreshed data retains the complete previous snapshot and is retried next interval. Disabled payloads are neither read nor validated. Root `data/` is not packaged as a production fallback. See [development settings](development.md#local-connection-configuration).
