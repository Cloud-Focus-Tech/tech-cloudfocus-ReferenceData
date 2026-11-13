# Reference-data files and catalog

All counts and shapes below were obtained by parsing the checked-in JSON on 2026-11-13. The directory contains **28 `v1.json` dataset files plus `index.json`**. No Java code currently reads them.

## Catalog format

[data/index.json](../data/index.json) contains a numeric `schemaVersion` of `1` and a `collections` array of seven metadata records. For example:

```json
{
  "name": "countries",
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
| `kind` | `set` for currencies; `keyValue` for the other six indexed collections |
| `version` | `v1` for every entry |
| `blob` | Path that resolves relative to `data/` in this checkout |
| `allowedRoles` | Always `["ReferenceData.Reader"]`; unused by the application |
| `hasSortOrder` | True for the three workflow collections; false for the other four |

The indexed names, in index order, are `countries`, `timezones`, `languages`, `currencies`, `workflow-priority`, `workflow-status`, and `workflow-doc-type`. All seven referenced files exist. There is no `count` field in the index.

The word `blob` and role metadata could support a future storage/access design, but the repository does not establish a cloud provider, blob client, role enforcement mechanism, or loader. Do not assume one is implemented.

## Dataset inventory

Each dataset path is `data/<directory>/v1.json`. `Indexed` means it appears in `index.json`, not that it is served by the API. Counts are top-level object keys or array elements. All object datasets have a `name` field in their records; the last column lists additional observed fields.

| Directory | Entries | Shape | Indexed | Additional fields |
| --- | ---: | --- | --- | --- |
| `address-countries` | 249 | Object | Yes, as `countries` | None |
| `address-delivery-type` | 6 | Object | No | None |
| `address-directionals` | 8 | Object | No | None |
| `address-military-regions` | 3 | Object | No | None |
| `address-states` | 51 | Object | No | None |
| `address-street-suffix` | 202 | Object | No | `aliases` |
| `address-territory` | 5 | Object | No | None |
| `address-type` | 6 | Object | No | None |
| `address-unit` | 24 | Object | No | `requiresIdentifier` |
| `currencies` | 3 | Array | Yes | String elements |
| `demographic-education` | 10 | Object | No | None |
| `demographic-employment` | 10 | Object | No | None |
| `demographic-living` | 8 | Object | No | None |
| `demographic-marital-status` | 5 | Object | No | None |
| `demographic-race` | 7 | Object | No | None |
| `demographic-response-status` | 5 | Object | No | None |
| `languages` | 7927 | Object | Yes | None |
| `medical-claim-status` | 12 | Object | No | None |
| `medical-coding-pos` | 52 | Object | No | None |
| `medical-coding-service` | 44 | Object | No | None |
| `medical-coverage` | 8 | Object | No | None |
| `medical-plantypes` | 5 | Object | No | None |
| `medical-relationship` | 15 | Object | No | None |
| `medical-specialty` | 65 | Object | No | None |
| `timezones` | 597 | Object | Yes | `utcOffset`, `utcOffsetAsOf` |
| `workflow-doc-type` | 34 | Object | Yes | `category`, `description`, `sortOrder` |
| `workflow-priority` | 6 | Object | Yes | `description`, `level`, `sortOrder` |
| `workflow-status` | 17 | Object | Yes | `category`, `isTerminal`, `sortOrder` |

The 21 unindexed datasets are not discoverable through the index. The three indexed workflow datasets are also absent from the runtime's hard-coded collection list.

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

## Guidance for future data integration

These are design considerations for future work, not existing application behavior:

1. Decide whether discovery should follow the index or include all dataset directories; they currently differ.
2. Choose a file delivery strategy: classpath resources, an external directory, or remote storage. Root `data/` is not configured as a Maven resource.
3. Support both array and keyed-object payloads and retain dataset-specific fields; the Java `Collection` summary cannot represent item data.
4. Define version selection, item/detail schemas, sort behavior, access rules, and missing-file/unknown-ID errors before presenting these as API guarantees.
5. Validate catalog paths, metadata, JSON shape, and counts when implementing loading. Keep the hard-coded list and these docs in sync until it is replaced.
