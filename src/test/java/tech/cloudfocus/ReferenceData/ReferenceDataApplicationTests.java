package tech.cloudfocus.ReferenceData;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import tech.cloudfocus.ReferenceData.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import tech.cloudfocus.ReferenceData.repository.CollectionRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "app.storage.refresh-interval-ms=3600000",
        "app.cors.allowed-origins=https://developer.lab.cloudfocus.tech,http://localhost:3000"})
@AutoConfigureMockMvc
class ReferenceDataApplicationTests {

    @TestBean(methodName = "fixtureProvider")
    StorageProvider storageProvider;

    static StorageProvider fixtureProvider() throws Exception { return FixtureStorage.fromData(); }

    @Autowired
    CollectionRepository repository;

    @Autowired
    MockMvc mvc;

    @Test
    void allowsBrowserReadsFromConfiguredOriginsIncludingErrorResponses() throws Exception {
        for (String origin : java.util.List.of("https://developer.lab.cloudfocus.tech", "http://localhost:3000")) {
            mvc.perform(get("/api/collections").header("Origin", origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
            mvc.perform(get("/api/collection/missing").header("Origin", origin))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    @Test
    void handlesBrowserPreflightForReadRequests() throws Exception {
        mvc.perform(options("/api/health/ready")
                .header("Origin", "https://developer.lab.cloudfocus.tech")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://developer.lab.cloudfocus.tech"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,HEAD"))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void rejectsUnlistedBrowserOrigins() throws Exception {
        for (String origin : java.util.List.of("https://untrusted.example", "http://developer.lab.cloudfocus.tech")) {
            mvc.perform(get("/api/collections").header("Origin", origin))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            mvc.perform(options("/api/collections").header("Origin", origin)
                    .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void rejectsPreflightForWriteRequests() throws Exception {
        mvc.perform(options("/api/collections")
                .header("Origin", "https://developer.lab.cloudfocus.tech")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void servesCanonicalBackstageCatalogAndStandaloneDefinition() throws Exception {
        byte[] catalog = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("catalog-info.yaml"));
        mvc.perform(get("/api/catalog-info.yaml")).andExpect(status().isOk())
                .andExpect(content().bytes(catalog));
        java.util.List<java.util.Map<String, Object>> entities = new java.util.ArrayList<>();
        for (Object entity : new org.yaml.snakeyaml.Yaml().loadAll(new String(catalog, java.nio.charset.StandardCharsets.UTF_8))) {
            @SuppressWarnings("unchecked")
            var parsed = (java.util.Map<String, Object>) entity;
            entities.add(parsed);
        }
        assertEquals(java.util.List.of("Component", "API"), entities.stream().map(e -> e.get("kind")).toList());
        mvc.perform(get("/api/backstage/openapi.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("https://api.lab.cloudfocus.tech/rds"))
                .andExpect(jsonPath("$.paths['/collections'].get").exists())
                .andExpect(jsonPath("$.paths['/backstage/openapi.json']").doesNotExist());
        mvc.perform(get("/api/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value(".."));
    }

    @Test
    void exposesOpenApiWithRelativeServerAndAllApplicationPaths() throws Exception {
        mvc.perform(get("/api/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ReferenceData API"))
                .andExpect(jsonPath("$.servers[0].url").value(".."))
                .andExpect(jsonPath("$.paths['/collections'].get").exists())
                .andExpect(jsonPath("$.paths['/collection/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/collection/{id}/items'].get").exists())
                .andExpect(jsonPath("$.paths['/health/live'].get").exists())
                .andExpect(jsonPath("$.paths['/health/ready'].get").exists())
                .andExpect(jsonPath("$.paths['/api/collections']").doesNotExist());
        mvc.perform(get("/api/v3/api-docs.yaml")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi:")));
    }

    @Test
    void exposesSwaggerAssetsAndRelativeConfiguration() throws Exception {
        mvc.perform(get("/api/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/api/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("../v3/api-docs"));
        mvc.perform(get("/api/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("../v3/api-docs/swagger-config")));
    }

	@Test
	void contextLoads() {
		assertEquals(7, repository.findAll().size());
	}

}
