package de.merkeg.shawty.config;

import de.merkeg.shawty.filestore.StorageType;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "app")
public interface ApplicationConfig {
    String bucket();
    String baseUrl();

    @WithDefault("S3")
    StorageType storage();

    @WithDefault("/var/shawty/files")
    String localStoragePath();

    Optional<String> adminApiKey();
}
