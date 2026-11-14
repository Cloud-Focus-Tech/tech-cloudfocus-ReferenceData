package tech.cloudfocus.ReferenceData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import tech.cloudfocus.ReferenceData.storage.StorageProvider;

public class FixtureStorage implements StorageProvider {
    public final Map<String, String> files = new HashMap<>();
    public final Map<String, String> etags = new HashMap<>();
    public int reads;

    public static FixtureStorage fromData() throws Exception {
        FixtureStorage storage = new FixtureStorage();
        try (var paths = Files.walk(Path.of("data"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                storage.put(Path.of("data").relativize(path).toString().replace('\\', '/'), Files.readString(path));
            }
        }
        return storage;
    }

    public void put(String path, String json) {
        files.put(path, json);
        etags.put(path, "etag-" + (Integer.parseInt(etags.getOrDefault(path, "etag-0").substring(5)) + 1));
    }

    @Override
    public String getETag(String path) {
        if (!files.containsKey(path)) { throw new IllegalStateException("Missing fixture: " + path); }
        return etags.get(path);
    }

    @Override
    public byte[] read(String path, String expectedETag) {
        if (!getETag(path).equals(expectedETag)) { throw new IllegalStateException("ETag changed"); }
        reads++;
        return files.get(path).getBytes(StandardCharsets.UTF_8);
    }
}
