package tech.cloudfocus.ReferenceData.catalog;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** A standalone view for consumers that embed the definition away from its source URL. */
@Hidden
@RestController
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class BackstageOpenApiController {
    private final OpenApiWebMvcResource openApi;
    private final JsonMapper mapper;
    private final String publicBaseUrl;

    public BackstageOpenApiController(OpenApiWebMvcResource openApi, JsonMapper mapper,
            @Value("${app.openapi.public-base-url}") String publicBaseUrl) {
        URI uri = URI.create(publicBaseUrl);
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("OPENAPI_PUBLIC_BASE_URL must be an absolute HTTP(S) URL without credentials, query or fragment");
        }
        this.openApi = openApi;
        this.mapper = mapper;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/api/backstage/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode definition(HttpServletRequest request) throws com.fasterxml.jackson.core.JsonProcessingException {
        // Parse into a new tree so the normal relative-server document stays unchanged.
        ObjectNode definition = (ObjectNode) mapper.readTree(
                openApi.openapiJson(request, "/api/v3/api-docs", request.getLocale()));
        definition.putArray("servers").addObject().put("url", publicBaseUrl);
        return definition;
    }
}
