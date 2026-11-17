package tech.cloudfocus.ReferenceData;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.convention.TestBean;
import tech.cloudfocus.ReferenceData.storage.StorageProvider;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "app.storage.refresh-interval-ms=3600000",
                "ENABLED_COLLECTIONS=timezones"})
class ItemExistenceHttpTests {
    @TestBean(methodName = "fixtureProvider")
    StorageProvider storageProvider;

    static StorageProvider fixtureProvider() throws Exception { return FixtureStorage.fromData(); }

    @Autowired
    Environment environment;

    @Test
    void handlesSlashIdsThroughActualHttpServerAndDecodesQueryOnlyOnce() throws Exception {
        String base = "http://localhost:" + environment.getRequiredProperty("local.server.port") + "/api/collection/timezones";
        try (var client = HttpClient.newHttpClient()) {
            for (String[] example : new String[][] {
                    {"/item/exists?itemId=Africa%2FAbidjan", "Africa/Abidjan", "true"},
                    {"/item/exists?itemId=Africa%2FNotReal", "Africa/NotReal", "false"},
                    {"/item/exists?itemId=Africa%252FAbidjan", "Africa%2FAbidjan", "false"},
                    {"/item/Africa/Abidjan/exists", "Africa/Abidjan", "true"},
                    {"/item/Africa/NotReal/exists", "Africa/NotReal", "false"},
                    {"/Item/Africa/Abidjan/exists", "Africa/Abidjan", "true"},
                    {"/item/exists?itemId=Etc%2FGMT%2B1", "Etc/GMT+1", "true"},
                    {"/item/America/Argentina/Buenos_Aires/exists", "America/Argentina/Buenos_Aires", "true"}}) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + example[0])).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                var json = JsonMapper.builder().build().readTree(response.body());
                assertEquals(2, json.size());
                assertEquals(example[1], json.path("itemId").stringValue());
                assertEquals(Boolean.parseBoolean(example[2]), json.path("exists").booleanValue());
            }
        }
    }

    @Test
    void retrievesSlashIdsThroughActualHttpServerAndDecodesQueryOnlyOnce() throws Exception {
        String base = "http://localhost:" + environment.getRequiredProperty("local.server.port") + "/api/collection/timezones";
        try (var client = HttpClient.newHttpClient()) {
            for (String[] example : new String[][] {
                    {"/item/Africa/Abidjan", "Africa/Abidjan"},
                    {"/item/America/Argentina/Buenos_Aires", "America/Argentina/Buenos Aires"},
                    {"/item?itemId=Africa%2FAbidjan", "Africa/Abidjan"},
                    {"/item?itemId=Etc%2FGMT%2B1", "Etc/GMT+1"}}) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + example[0])).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                var json = JsonMapper.builder().build().readTree(response.body());
                assertEquals(example[1], json.path("name").stringValue());
                assertTrue(json.has("utcOffset"));
                assertFalse(json.has("exists"));
            }
            for (String path : new String[] {"/item/Africa/NotReal", "/item?itemId=Africa%252FAbidjan"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(404, response.statusCode(), response.body());
            }
        }
    }
}
