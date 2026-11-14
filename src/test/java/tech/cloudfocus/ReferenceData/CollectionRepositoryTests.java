package tech.cloudfocus.ReferenceData;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tech.cloudfocus.ReferenceData.repository.CollectionRepository;
import tech.cloudfocus.ReferenceData.storage.StorageProvider;
import tools.jackson.databind.node.ArrayNode;

class CollectionRepositoryTests {
    @Test
    void loadsRealCatalogAndRefreshesIndividualBlobWithoutIndexChange() throws Exception {
        var storage = FixtureStorage.fromData();
        var repository = new CollectionRepository(storage);
        repository.afterPropertiesSet();
        assertEquals(7, repository.findAll().size());
        assertEquals("countries", repository.findAll().getFirst().getName());
        assertEquals(7927, repository.findByName("languages").orElseThrow().getCount());
        assertEquals(8, storage.reads);
        repository.reload();
        assertEquals(8, storage.reads, "Unchanged ETags must avoid content downloads");
        storage.put("currencies/v1.json", "[\"USD\",\"GBP\"]");
        repository.reload();
        assertEquals(9, storage.reads, "Only the changed blob should be downloaded");
        assertEquals(2, repository.findByName("currencies").orElseThrow().getCount());
        ((ArrayNode) repository.findItems("currencies").orElseThrow()).removeAll();
        repository.findByName("currencies").orElseThrow().setCount(999);
        assertEquals(2, repository.findItems("currencies").orElseThrow().size());
        assertEquals(2, repository.findByName("currencies").orElseThrow().getCount());
    }

    @Test
    void failedRefreshRetainsWholeSnapshotAndRetriesNextTime() throws Exception {
        var storage = FixtureStorage.fromData();
        var repository = new CollectionRepository(storage);
        repository.reload();
        storage.put("currencies/v1.json", "[\"GBP\"]");
        storage.put("workflow-status/v1.json", "not json");
        repository.refresh();
        assertEquals(3, repository.findByName("currencies").orElseThrow().getCount());
        assertEquals(17, repository.findByName("workflow-status").orElseThrow().getCount());
        storage.put("workflow-status/v1.json", "{}");
        repository.refresh();
        assertEquals(1, repository.findByName("currencies").orElseThrow().getCount());
        assertEquals(0, repository.findByName("workflow-status").orElseThrow().getCount());
    }

    @Test
    void changedIndexRemovesCollectionsAndLoadsNewPaths() throws Exception {
        var storage = FixtureStorage.fromData();
        var repository = new CollectionRepository(storage);
        repository.reload();
        storage.put("index.json", """
                {"schemaVersion":1,"collections":[
                  {"name":"new","kind":"set","version":"v2","blob":"new/v2.json"}]}
                """);
        storage.put("new/v2.json", "[\"value\"]");
        repository.reload();
        assertEquals(1, repository.findAll().size());
        assertTrue(repository.findByName("currencies").isEmpty());
        assertEquals("v2", repository.findByName("new").orElseThrow().getVersion());
    }

    @Test
    void startupFailsForMissingBlobAndInvalidData() throws Exception {
        var storage = FixtureStorage.fromData();
        storage.files.remove("languages/v1.json");
        var repository = new CollectionRepository(storage);
        assertThrows(IllegalStateException.class, repository::afterPropertiesSet);
        assertFalse(repository.isLoaded());
        for (String invalid : new String[] {"null", "{}", "[42]", "[\"USD\"] {}", "invalid"}) {
            var invalidStorage = FixtureStorage.fromData();
            invalidStorage.put("currencies/v1.json", invalid);
            assertThrows(RuntimeException.class, new CollectionRepository(invalidStorage)::afterPropertiesSet);
        }
    }

    @Test
    void rejectsDuplicateKeysAndWrongSchemaAndTraversal() throws Exception {
        for (String invalid : new String[] {
                "{\"schemaVersion\":2,\"collections\":[]}",
                "{\"schemaVersion\":1,\"schemaVersion\":1,\"collections\":[]}",
                """
                {"schemaVersion":1,"collections":[
                  {"name":"bad","version":"v1","kind":"set","blob":"../bad.json"}]}
                """}) {
            var storage = FixtureStorage.fromData();
            storage.put("index.json", invalid);
            assertThrows(RuntimeException.class, new CollectionRepository(storage)::afterPropertiesSet);
        }
    }

    @Test
    void rejectsStorageChangeDuringBatch() throws Exception {
        var fixture = FixtureStorage.fromData();
        StorageProvider changing = new StorageProvider() {
            int indexChecks;
            public String getETag(String path) {
                if (path.equals("index.json") && ++indexChecks > 1) { return "changed"; }
                return fixture.getETag(path);
            }
            public byte[] read(String path, String etag) { return fixture.read(path, etag); }
        };
        var repository = new CollectionRepository(changing);
        assertThrows(IllegalStateException.class, repository::reload);
        assertFalse(repository.isLoaded());
    }
}
