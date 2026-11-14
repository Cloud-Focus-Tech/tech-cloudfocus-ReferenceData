package tech.cloudfocus.ReferenceData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobDownloadContentResponse;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import tech.cloudfocus.ReferenceData.storage.AzureBlobStorageProvider;

class AzureBlobStorageProviderTests {
    @Test
    @SuppressWarnings("unchecked")
    void readsMetadataAndUsesIfMatchForContentDownload() {
        var container = mock(BlobContainerClient.class);
        var blob = mock(BlobClient.class);
        var properties = mock(BlobProperties.class);
        Response<BlobProperties> response = mock(Response.class);
        var download = mock(BlobDownloadContentResponse.class);
        when(container.getBlobClient("index.json")).thenReturn(blob);
        when(blob.getPropertiesWithResponse(isNull(), any(Duration.class), eq(Context.NONE))).thenReturn(response);
        when(response.getValue()).thenReturn(properties);
        when(properties.getETag()).thenReturn("etag-1");
        when(blob.downloadContentWithResponse(isNull(), any(BlobRequestConditions.class), any(Duration.class), eq(Context.NONE)))
                .thenReturn(download);
        when(download.getValue()).thenReturn(BinaryData.fromString("{}"));
        var provider = new AzureBlobStorageProvider(container);
        assertEquals("etag-1", provider.getETag("index.json"));
        assertArrayEquals(new byte[] {'{', '}'}, provider.read("index.json", "etag-1"));
        var conditions = ArgumentCaptor.forClass(BlobRequestConditions.class);
        verify(blob).downloadContentWithResponse(isNull(), conditions.capture(), eq(Duration.ofSeconds(30)), eq(Context.NONE));
        assertEquals("etag-1", conditions.getValue().getIfMatch());
    }
}
