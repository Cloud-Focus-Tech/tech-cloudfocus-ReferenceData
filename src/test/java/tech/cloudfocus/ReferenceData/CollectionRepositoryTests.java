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
        assertEquals(28, repository.findAll().size());
        assertTrue(repository.findAll().stream().allMatch(c -> c.isEnabled()));
        assertEquals("address-countries", repository.findAll().getFirst().getName());
        assertEquals(7927, repository.findByName("languages").orElseThrow().getCount());
        assertEquals(29, storage.reads);
        repository.reload();
        assertEquals(29, storage.reads, "Unchanged ETags must avoid content downloads");
        storage.put("currencies/v1.json", "[\"USD\",\"GBP\"]");
        repository.reload();
        assertEquals(30, storage.reads, "Only the changed blob should be downloaded");
        assertEquals(2, repository.findByName("currencies").orElseThrow().getCount());
        ((ArrayNode) repository.findItems("currencies").orElseThrow()).removeAll();
        repository.findByName("currencies").orElseThrow().setCount(999);
        repository.findByName("currencies").orElseThrow().setEnabled(false);
        assertEquals(2, repository.findItems("currencies").orElseThrow().size());
        assertEquals(2, repository.findByName("currencies").orElseThrow().getCount());
        assertTrue(repository.findByName("currencies").orElseThrow().isEnabled());
    }

    @Test
    void loadsOnlySelectedDataButListsWholeCatalogAndRefreshesEnabledData() throws Exception {
        var fixture = FixtureStorage.fromData();
        StorageProvider selectedOnly = new StorageProvider() {
            private void check(String path) {
                assertTrue(path.equals("index.json") || path.equals("currencies/v1.json"),
                        "Disabled data must not be downloaded or have its ETag checked: " + path);
            }
            public String getETag(String path) { check(path); return fixture.getETag(path); }
            public byte[] read(String path, String etag) { check(path); return fixture.read(path, etag); }
        };
        var repository = new CollectionRepository(selectedOnly, " currencies, currencies ");
        repository.reload();
        assertEquals(28, repository.findAll().size());
        assertEquals("address-countries", repository.findAll().getFirst().getName());
        assertFalse(repository.findAll().getFirst().isEnabled());
        assertNull(repository.findAll().getFirst().getCount());
        assertTrue(repository.findByName("address-countries").isEmpty());
        assertTrue(repository.findItems("address-countries").isEmpty());
        assertEquals(3, repository.findByName("currencies").orElseThrow().getCount());
        assertEquals(2, fixture.reads);
        repository.reload();
        assertEquals(2, fixture.reads);
        fixture.put("currencies/v1.json", "[\"GBP\"]");
        repository.reload();
        assertEquals(3, fixture.reads);
        assertEquals(1, repository.findByName("currencies").orElseThrow().getCount());
    }

    @Test
    void emptySelectionLoadsOnlyIndexAndIsReady() throws Exception {
        var storage = FixtureStorage.fromData();
        storage.files.keySet().removeIf(path -> !path.equals("index.json"));
        var repository = new CollectionRepository(storage, "  ");
        repository.reload();
        assertTrue(repository.isLoaded());
        assertEquals(28, repository.findAll().size());
        assertTrue(repository.findAll().stream().noneMatch(c -> c.isEnabled()));
        assertTrue(repository.findAll().stream().allMatch(c -> c.getCount() == null));
        assertTrue(repository.findItems("currencies").isEmpty());
        assertEquals(1, storage.reads);
    }

    @Test
    void validatesSelectionNamesAndRejectsMalformedConfiguration() throws Exception {
        var storage = FixtureStorage.fromData();
        for (String selection : new String[] {"currencies,", ",countries", "countries,,currencies", "*,countries"}) {
            assertThrows(IllegalArgumentException.class, () -> new CollectionRepository(storage, selection));
        }
        for (String selection : new String[] {"missing", "Currencies"}) {
            var repository = new CollectionRepository(storage, selection);
            var error = assertThrows(IllegalStateException.class, repository::reload);
            assertTrue(error.getMessage().contains(selection));
            assertFalse(repository.isLoaded());
        }
    }

    @Test
    void refreshUpdatesDisabledMetadataWithoutLoadingItsData() throws Exception {
        var storage = FixtureStorage.fromData();
        var repository = new CollectionRepository(storage, "currencies");
        repository.reload();
        storage.put("index.json", """
                {"schemaVersion":1,"collections":[
                  {"name":"new","kind":"set","version":"v2","blob":"missing/v2.json"},
                  {"name":"currencies","kind":"set","version":"v1","blob":"currencies/v1.json"}]}
                """);
        repository.reload();
        assertEquals(2, repository.findAll().size());
        var disabled = repository.findAll().getFirst();
        assertEquals("new", disabled.getName());
        assertEquals("v2", disabled.getVersion());
        assertFalse(disabled.isEnabled());
        assertNull(disabled.getCount());
        assertEquals(3, storage.reads, "Only the changed index is downloaded");
        assertEquals(3, repository.findByName("currencies").orElseThrow().getCount());

        storage.put("currencies/v1.json", "invalid");
        repository.refresh();
        assertEquals(3, repository.findByName("currencies").orElseThrow().getCount());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void removingConfiguredCollectionRejectsRefreshAndRetainsPreviousSnapshot() throws Exception {
        var storage = FixtureStorage.fromData();
        var repository = new CollectionRepository(storage, "currencies");
        repository.reload();
        storage.put("index.json", "{\"schemaVersion\":1,\"collections\":[]}");
        repository.refresh();
        assertEquals(28, repository.findAll().size());
        assertEquals(3, repository.findByName("currencies").orElseThrow().getCount());
        assertTrue(repository.isLoaded());
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
    void validatesMetadataEvenWhenNoCollectionsAreEnabled() throws Exception {
        for (String invalid : new String[] {
                """
                {"schemaVersion":1,"collections":[
                  {"name":"bad","version":"v1","kind":"set","blob":"../bad.json"}]}
                """,
                """
                {"schemaVersion":1,"collections":[
                  {"name":"bad","version":"v1","kind":"unsupported","blob":"bad.json"}]}
                """,
                """
                {"schemaVersion":1,"collections":[
                  {"name":"duplicate","version":"v1","kind":"set","blob":"a.json"},
                  {"name":"duplicate","version":"v1","kind":"set","blob":"b.json"}]}
                """}) {
            var storage = FixtureStorage.fromData();
            storage.put("index.json", invalid);
            var repository = new CollectionRepository(storage, "");
            assertThrows(IllegalStateException.class, repository::reload);
            assertFalse(repository.isLoaded());
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
