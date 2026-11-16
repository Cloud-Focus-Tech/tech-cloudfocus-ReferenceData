package tech.cloudfocus.ReferenceData.config;

import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI referenceDataOpenApi() {
        return new OpenAPI().info(new Info().title("ReferenceData API").version("v1")
                .description("Versioned reference collections loaded from cloud storage."))
                // Relative to the spec URL: /api/v3/api-docs -> /api/, /rds/v3/api-docs -> /rds/.
                .servers(List.of(new Server().url("..").description("Current API base path")));
    }

    @Bean
    OpenApiCustomizer apiRelativePaths() {
        return document -> {
            Paths paths = new Paths();
            if (document.getPaths() != null) {
                document.getPaths().forEach((path, operations) ->
                        paths.addPathItem(path.startsWith("/api/") ? path.substring(4) : path, operations));
            }
            document.setPaths(paths);
        };
    }
}
