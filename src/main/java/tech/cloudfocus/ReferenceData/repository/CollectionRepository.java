package tech.cloudfocus.ReferenceData.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import tech.cloudfocus.ReferenceData.model.Collection;
import tech.cloudfocus.ReferenceData.storage.StorageProvider;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

@Repository
public class CollectionRepository implements InitializingBean {
    private static final String INDEX = "index.json";
    private static final Logger log = LoggerFactory.getLogger(CollectionRepository.class);
    private final StorageProvider storage;
    private final boolean enableAll;
    private final Set<String> enabledNames;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private volatile Snapshot snapshot;

    private record Document(String etag, JsonNode json) { }
    private record CatalogCollection(String name, String kind, String version, JsonNode items,
            Set<String> setItemIds) {
        CatalogCollection(String name, String kind, String version, JsonNode items) {
            this(name, kind, version, items, indexSet(items));
        }

        private static Set<String> indexSet(JsonNode items) {
            if (items == null || !items.isArray()) { return Set.of(); }
            Set<String> index = new HashSet<>();
            for (JsonNode item : items) { index.add(item.stringValue()); }
            return Set.copyOf(index);
        }

        boolean enabled() { return items != null; }
        Collection summary() {
            return new Collection(name, kind, version, enabled() ? items.size() : null, enabled());
        }
    }
    private record Snapshot(Map<String, Document> documents, Map<String, CatalogCollection> collections) { }

    public CollectionRepository(StorageProvider storage) { this(storage, "*"); }

    @Autowired
    public CollectionRepository(StorageProvider storage,
            @Value("${app.collections.enabled:*}") String enabledCollections) {
        this.storage = storage;
        String selection = enabledCollections.trim();
        this.enableAll = selection.equals("*");
        this.enabledNames = selection.isEmpty() || enableAll ? Set.of()
                : Set.copyOf(Arrays.stream(selection.split(",", -1)).map(String::trim).toList());
        if (enabledNames.contains("") || enabledNames.contains("*")) {
            throw new IllegalArgumentException("ENABLED_COLLECTIONS must be *, empty, or comma-separated collection names");
        }
    }

    @Override
    public void afterPropertiesSet() { reload(); }

    @Scheduled(fixedDelayString = "${app.storage.refresh-interval-ms}",
            initialDelayString = "${app.storage.refresh-interval-ms}")
    public void refresh() {
        try {
            reload();
        } catch (RuntimeException failure) {
            // SDK exception messages can contain storage URLs; do not log their contents.
            log.error("Reference data refresh failed ({}); retaining last complete snapshot",
                    failure.getClass().getSimpleName());
        }
    }

    /** Stage all data before atomically publishing it to readers. */
    public synchronized void reload() {
        Snapshot previous = snapshot;
        Map<String, Document> documents = new LinkedHashMap<>();
        JsonNode index = load(INDEX, previous, documents);
        require(index.isObject() && index.path("schemaVersion").isIntegralNumber()
                && index.path("schemaVersion").intValue() == 1, "Unsupported index schemaVersion");
        JsonNode entries = index.path("collections");
        require(entries.isArray(), "Index collections must be an array");
        Map<String, CatalogCollection> collections = new LinkedHashMap<>();
        for (JsonNode entry : entries) {
            String name = text(entry, "name");
            String kind = text(entry, "kind");
            String version = text(entry, "version");
            String path = text(entry, "blob");
            require(!path.startsWith("/") && !path.contains("\\") && !path.contains(":")
                    && List.of(path.split("/", -1)).stream().noneMatch(p -> p.isBlank() || p.equals("..") || p.equals("."))
                    && !path.equals(INDEX), "Invalid catalog blob path");
            require(!collections.containsKey(name), "Duplicate collection name: " + name);
            require(kind.equals("set") || kind.equals("keyValue"), "Unsupported collection kind: " + kind);
            if (!enableAll && !enabledNames.contains(name)) {
                collections.put(name, new CatalogCollection(name, kind, version, null));
                continue;
            }
            JsonNode items = load(path, previous, documents);
            if (kind.equals("set")) {
                require(items.isArray(), "Set data must be an array: " + path);
                for (JsonNode item : items) { require(item.isString(), "Set entries must be strings: " + path); }
            } else {
                require(items.isObject(), "Key/value data must be an object: " + path);
                for (var item : items.properties()) {
                    require(!item.getKey().isBlank() && item.getValue().isObject(),
                            "Key/value entries must have a key and object value: " + path);
                    text(item.getValue(), "name");
                    if (entry.path("hasSortOrder").asBoolean(false)) {
                        require(item.getValue().path("sortOrder").isIntegralNumber(), "Missing sortOrder: " + path);
                    }
                }
            }
            collections.put(name, new CatalogCollection(name, kind, version, items));
        }
        Set<String> missing = new LinkedHashSet<>(enabledNames);
        missing.removeAll(collections.keySet());
        require(missing.isEmpty(), "ENABLED_COLLECTIONS names not found in index: " + missing);
        // A concurrent storage update must not publish a partially changed batch.
        for (var document : documents.entrySet()) {
            require(Objects.equals(document.getValue().etag(), storage.getETag(document.getKey())),
                    "Storage changed during collection loading; retry on next refresh");
        }
        if (previous == null || !documents.equals(previous.documents())) {
            // Keep index order for discovery while allowing direct lookup by name.
            snapshot = new Snapshot(Map.copyOf(documents),
                    Collections.unmodifiableMap(new LinkedHashMap<>(collections)));
            log.info("Loaded {} of {} available reference data collections",
                    collections.values().stream().filter(CatalogCollection::enabled).count(), collections.size());
        }
    }

    private JsonNode load(String path, Snapshot previous, Map<String, Document> documents) {
        if (documents.containsKey(path)) { return documents.get(path).json(); }
        String etag = storage.getETag(path);
        require(etag != null && !etag.isBlank(), "Storage returned an empty ETag");
        Document old = previous == null ? null : previous.documents().get(path);
        Document loaded = old != null && old.etag().equals(etag) ? old
                : new Document(etag, mapper.readTree(storage.read(path, etag)));
        require(loaded.json() != null && !loaded.json().isNull(), "Empty JSON document: " + path);
        documents.put(path, loaded);
        return loaded.json();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isString() && !value.stringValue().isBlank(), "Missing or invalid field: " + field);
        return value.stringValue();
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    public boolean isLoaded() { return snapshot != null; }

    public List<Collection> findAll() {
        Snapshot current = snapshot;
        return current == null ? List.of() : current.collections().values().stream().map(CatalogCollection::summary).toList();
    }

    public Optional<Collection> findByName(String name) { return findLoaded(name).map(CatalogCollection::summary); }

    public Optional<JsonNode> findItems(String name) {
        return findLoaded(name).map(collection -> collection.items().deepCopy());
    }

    public Optional<Boolean> itemExists(String name, String itemId) {
        return findLoaded(name)
                .map(collection -> collection.items().isObject()
                        ? collection.items().has(itemId) : collection.setItemIds().contains(itemId));
    }

    public Optional<JsonNode> findItem(String name, String itemId) {
        return findLoaded(name).flatMap(collection -> {
            if (collection.items().isObject()) {
                return Optional.ofNullable(collection.items().get(itemId)).map(JsonNode::deepCopy);
            }
            return collection.setItemIds().contains(itemId)
                    ? Optional.of(StringNode.valueOf(itemId)) : Optional.empty();
        });
    }

    private Optional<CatalogCollection> findLoaded(String name) {
        Snapshot current = snapshot;
        return current == null ? Optional.empty()
                : Optional.ofNullable(current.collections().get(name)).filter(CatalogCollection::enabled);
    }
}
