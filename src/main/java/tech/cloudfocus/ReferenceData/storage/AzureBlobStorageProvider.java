package tech.cloudfocus.ReferenceData.storage;

import java.time.Duration;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobRequestConditions;

public final class AzureBlobStorageProvider implements StorageProvider {
    private final BlobContainerClient container;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public AzureBlobStorageProvider(BlobContainerClient container) { this.container = container; }

    @Override
    public String getETag(String path) {
        return container.getBlobClient(path)
                .getPropertiesWithResponse(null, TIMEOUT, Context.NONE).getValue().getETag();
    }

    @Override
    public byte[] read(String path, String expectedETag) {
        return container.getBlobClient(path).downloadContentWithResponse(null,
                new BlobRequestConditions().setIfMatch(expectedETag), TIMEOUT, Context.NONE)
                .getValue().toBytes();
    }
}
