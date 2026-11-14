package tech.cloudfocus.ReferenceData.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.azure.storage.blob.BlobContainerClientBuilder;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class StorageConfiguration {
    @Bean
    @ConditionalOnMissingBean(StorageProvider.class)
    StorageProvider storageProvider(
            @Value("${app.storage.connection-string}") String connectionString,
            @Value("${app.storage.container-name}") String containerName,
            @Value("${app.storage.refresh-interval-ms}") long refreshInterval) {
        if (connectionString.isBlank() || containerName.isBlank()) {
            throw new IllegalArgumentException("STORAGE_CONNECTION_STRING and STORAGE_CONTAINER_NAME are required");
        }
        if (refreshInterval <= 0) {
            throw new IllegalArgumentException("STORAGE_REFRESH_INTERVAL_MS must be positive");
        }
        return new AzureBlobStorageProvider(new BlobContainerClientBuilder()
                .connectionString(connectionString).containerName(containerName).buildClient());
    }
}
