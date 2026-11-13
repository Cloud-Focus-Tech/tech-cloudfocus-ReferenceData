# ReferenceData: agent starting point

Reviewed against the working-tree source on **2026-11-13**. These documents describe the current implementation; suggested next steps are explicitly identified as suggestions. Recheck the source when changing behavior.

ReferenceData is a Java Spring Boot REST service skeleton accompanied by versioned reference-data JSON files. The application currently returns a hard-coded collection list and placeholder detail/item responses. **The JSON files are not loaded or served by the application.**

Read these documents in order as needed:

| Document | Contents |
| --- | --- |
| [Architecture](architecture.md) | Source map, dependency flow, runtime configuration, implemented capabilities and gaps |
| [API](api.md) | All five routes, exact response examples, current error and health behavior |
| [Data catalog](data-catalog.md) | Index format, all 28 datasets, record shapes, catalog/runtime differences |
| [Development](development.md) | Build/run commands, tests, verification limits, guidance for changes |

## Facts to keep in mind

- The base API path is `/api`; the collection list uses plural `/collections`, while detail/items use singular `/collection/{id}`.
- The list contains four entries. `data/index.json` lists seven collections, and `data/` contains 28 dataset files. These are three different inventories today.
- `countries` is the public collection name in both the repository and index; its file lives at `data/address-countries/v1.json`.
- Index metadata such as `allowedRoles` and `hasSortOrder` is not enforced by Java code.
- Both health endpoints always return `{"status":"UP"}` when their handlers execute.
- There is one Spring context-load test and no endpoint or dataset-loading tests.
- The POM pins Java 25 and Spring Boot 4.1.2; the wrapper points to Maven 3.10.0. Do not infer dependency versions from existing `target/` artifacts.

## Source of truth

Start with [pom.xml](../pom.xml), [the controller](../src/main/java/tech/cloudfocus/ReferenceData/controller/collections.java), [the repository](../src/main/java/tech/cloudfocus/ReferenceData/repository/CollectionRepository.java), and [the data index](../data/index.json). The working-tree files take precedence over these notes.

The root `HELP.md` is ignored by Git and contains generated framework guidance, including security links. Those links do not establish that authentication is implemented. `target/` is also ignored and may contain stale builds or exploratory files.

No application source, configuration, dependencies, or datasets were changed as part of this documentation review.
