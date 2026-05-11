package de.merkeg.shawty.filestore;

import de.merkeg.shawty.config.ApplicationConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class FileStoreProducer {

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    @S3Storage
    Instance<S3FileStore> s3FileStore;

    @Inject
    @LocalStorage
    Instance<LocalFileStore> localFileStore;

    @Produces
    @ApplicationScoped
    public FileStore produceFileStore() {
        StorageType storageType = applicationConfig.storage();
        log.info("Configuring FileStore with storage type: {}", storageType);
        return switch (storageType) {
            case LOCAL -> localFileStore.get();
            case S3 -> s3FileStore.get();
        };
    }
}


