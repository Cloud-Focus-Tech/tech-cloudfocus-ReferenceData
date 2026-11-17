package tech.cloudfocus.ReferenceData;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.cloudfocus.ReferenceData.storage.StorageProvider;

@SpringBootTest(properties = {"spring.config.import=", "app.storage.refresh-interval-ms=3600000",
        "ENABLED_COLLECTIONS=currencies,address-countries"})
@AutoConfigureMockMvc
class CollectionSelectionApplicationTests {
    @TestBean(methodName = "fixtureProvider")
    StorageProvider storageProvider;

    static StorageProvider fixtureProvider() throws Exception {
        var storage = FixtureStorage.fromData();
        // A disabled dataset need not exist in storage at all.
        storage.files.remove("languages/v1.json");
        return storage;
    }

    @Autowired
    MockMvc mvc;

    @Test
    void environmentSelectionListsAllMetadataAndServesOnlyEnabledCollections() throws Exception {
        mvc.perform(get("/api/collections")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(28))
                .andExpect(jsonPath("$[0].name").value("address-countries"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].count").value(249))
                .andExpect(jsonPath("$[2].name").value("timezones"))
                .andExpect(jsonPath("$[2].enabled").value(false))
                .andExpect(jsonPath("$[2].count").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/collection/currencies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.count").value(3));
        mvc.perform(get("/api/collection/currencies/items")).andExpect(status().isOk())
                .andExpect(content().json("[\"USD\",\"CAD\",\"EUR\"]"));
        for (String name : java.util.List.of("languages", "timezones", "missing")) {
            mvc.perform(get("/api/collection/" + name)).andExpect(status().isNotFound());
            mvc.perform(get("/api/collection/" + name + "/items")).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/health/ready")).andExpect(status().isOk());
    }
}
