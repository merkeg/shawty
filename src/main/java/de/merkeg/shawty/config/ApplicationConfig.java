package de.merkeg.shawty.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "app")
public interface ApplicationConfig {
    String bucket();
    String baseUrl();
}
