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
        mvc.perform(get("/api/collections")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(28));
        mvc.perform(get("/api/collection/currencies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
        mvc.perform(get("/api/collection/currencies/items")).andExpect(status().isOk())
                .andExpect(content().json("[\"USD\",\"CAD\",\"EUR\"]"));
        mvc.perform(get("/api/collection/address-countries/items")).andExpect(status().isOk())
                .andExpect(jsonPath("$.US.name").value("United States"));
        mvc.perform(get("/api/collection/address-countries/item/US/exists")).andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"US\",\"exists\":true}"))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/collection/currencies/item/USD/exists")).andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"USD\",\"exists\":true}"));
        mvc.perform(get("/api/collection/currencies/item/0/exists")).andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"0\",\"exists\":false}"));
        mvc.perform(get("/api/collection/currencies/item/usd/exists")).andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"usd\",\"exists\":false}"));
        mvc.perform(get("/api/collection/address-countries/item/missing/exists")).andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"missing\",\"exists\":false}"));
        mvc.perform(get("/api/collection/missing/item/US/exists")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/timezones/item/Africa/Abidjan/exists")).andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"Africa/Abidjan\",\"exists\":true}"));
        mvc.perform(get("/api/collection/timezones/item/Africa/NotReal/exists")).andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"Africa/NotReal\",\"exists\":false}"));
        mvc.perform(get("/api/collection/timezones/item/exists").param("itemId", "Africa/Abidjan"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"Africa/Abidjan\",\"exists\":true}"));
        mvc.perform(get("/api/collection/address-countries/Item/US/exists")).andExpect(status().isOk())
                .andExpect(content().json("{\"itemId\":\"US\",\"exists\":true}"));
        mvc.perform(get("/api/collection/address-countries/item/US")).andExpect(status().isOk())
                .andExpect(content().json("{\"name\":\"United States\"}"));
        mvc.perform(get("/api/collection/currencies/item/USD")).andExpect(status().isOk())
                .andExpect(content().json("\"USD\""));
        mvc.perform(get("/api/collection/medical-coding-pos/item/01")).andExpect(status().isOk())
                .andExpect(content().json("{\"name\":\"Pharmacy\"}"));
        mvc.perform(get("/api/collection/medical-coding-pos/item/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/currencies/item/usd")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/currencies/item/0")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/address-countries/item/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/missing/item/US")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/timezones/item/Africa/Abidjan")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Africa/Abidjan"))
                .andExpect(jsonPath("$.utcOffset").value("+00:00"));
        mvc.perform(get("/api/collection/timezones/item").param("itemId", "Africa/Abidjan"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Africa/Abidjan"));
        mvc.perform(get("/api/collection/timezones/item/Africa/NotReal")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/address-countries/items/US")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/timezones/item")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/collection/timezones/item").param("itemId", "")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/collection/timezones/item/exists")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/collection/timezones/item/exists").param("itemId", ""))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/collection/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collection/missing/items")).andExpect(status().isNotFound());
        mvc.perform(get("/api/health/ready")).andExpect(status().isOk());
    }
}
