package tech.cloudfocus.ReferenceData.storage;

/** Paths are relative to the storage root; reads must match the requested ETag. */
public interface StorageProvider {
    String getETag(String path);
    byte[] read(String path, String expectedETag);
}
