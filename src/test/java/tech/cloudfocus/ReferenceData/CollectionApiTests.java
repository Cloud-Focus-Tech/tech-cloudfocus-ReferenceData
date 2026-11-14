package tech.cloudfocus.ReferenceData;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tech.cloudfocus.ReferenceData.controller.collections;
import tech.cloudfocus.ReferenceData.repository.CollectionRepository;
import tech.cloudfocus.ReferenceData.service.CollectionService;

class CollectionApiTests {
    @Test
    void servesLoadedDataAndUnknownCollectionsReturn404() throws Exception {
        var repository = new CollectionRepository(FixtureStorage.fromData());
        repository.reload();
        var mvc = MockMvcBuilders.standaloneSetup(new collections(new CollectionService(repository))).build();
        mvc.perform(get("/api/collections")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/collection/currencies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
        mvc.perform(get("/api/collection/currencies/items")).andExpect(status().isOk())
                .andExpect(content().json("[\"USD\",\"CAD\",\"EUR\"]"));
        mvc.perform(get("/api/collection/countries/items")).andExpect(status().isOk())
                .andExpect(jsonPath("$.US.name").value("United States"));
        mvc.perform(get("/api/collection/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/missing/items")).andExpect(status().isNotFound());
        mvc.perform(get("/api/health/ready")).andExpect(status().isOk());
    }
}
