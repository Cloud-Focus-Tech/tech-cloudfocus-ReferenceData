# Backstage catalog registration

The root [catalog-info.yaml](../catalog-info.yaml) defines a Component named `reference-data` providing an API named `reference-data-api`. Both use owner `group:default/cloud-focus-tech` and lifecycle `experimental`. Update the owner if your existing Backstage group uses a different name, and update the lifecycle when appropriate.

## Published locations

After publishing and deploying the updated image:

- Register `https://api.lab.cloudfocus.tech/rds/catalog-info.yaml` in Backstage's **Register Existing Component** flow. The multi-document YAML registers both entities and their `providesApis` relationship.
- The API entity loads its definition with `$text` from `https://api.lab.cloudfocus.tech/rds/backstage/openapi.json`.
- Swagger UI remains at `https://api.lab.cloudfocus.tech/rds/swagger-ui.html`.

Locally, the equivalent routes are `/api/catalog-info.yaml` and `/api/backstage/openapi.json` on port 8080. The existing gateway prefix rewrite covers both; no additional Gateway route is needed for the catalog.

The root file is the source of truth for both GitHub discovery and application hosting. Maven copies only that file into `static/api` in the classpath; the Docker build includes it explicitly, and Spring serves it in JVM and native execution. There is no second YAML file to synchronize, and the resource configuration does not package `.env` or other root files.

For source-based registration, use `https://github.com/Cloud-Focus-Tech/tech-cloudfocus-ReferenceData/blob/master/catalog-info.yaml` with Backstage's GitHub integration. Even when registering from GitHub, the API definition is read from the deployed service.

## Backstage backend configuration

The backend must be able to reach the lab hostname and trust its lab CA. Merge this reader allowlist and, if using configuration-based registration, location into the existing configuration. Preserve other entries:

```yaml
backend:
  reading:
    allow:
      - host: api.lab.cloudfocus.tech
        paths: ['/rds/']

catalog:
  locations:
    - type: url
      target: https://api.lab.cloudfocus.tech/rds/catalog-info.yaml
      rules:
        - allow: [Component, API]
```

Use either the registration UI or configuration-based location to register the URL. For a Node-based backend using the internal CA, mount the CA certificate and configure its trust, for example with `NODE_EXTRA_CA_CERTS` pointing to the mounted PEM file. Keep TLS verification enabled.

Backstage's [descriptor format](https://backstage.io/docs/features/software-catalog/descriptor-format/#substitutions-in-the-descriptor-format) documents `$text` substitution and the reader allowlist.

## OpenAPI base URL

Backstage embeds the fetched definition rather than hosting it at its original URL. The normal Springdoc document uses a relative server URL for direct Swagger UI access; the Backstage view instead supplies `https://api.lab.cloudfocus.tech/rds` as an absolute server URL. It is generated from the same operations and schemas and does not modify the normal cached document.

`OPENAPI_PUBLIC_BASE_URL` overrides that server URL for another environment. If moving the service, also update the catalog's definition and metadata links. `BackstageOpenApiController` is hidden from generated operations and is disabled if `springdoc.api-docs.enabled=false`. Normal OpenAPI JSON/YAML and Swagger UI URLs remain unchanged.

No Azure credentials or registry tokens belong in the catalog file. No changes to the running Backstage application or catalog registration were made by adding these project files. Publish the new image and use the existing [deployment workflow](deployment.md) before registering the application-hosted URL.
