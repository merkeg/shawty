package de.merkeg.shawty.filestore;

import de.merkeg.shawty.config.ApplicationConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@ApplicationScoped
@LocalStorage
@Slf4j
public class LocalFileStore implements FileStore {

    @Inject
    ApplicationConfig applicationConfig;

    @Override
    public void store(String key, File file, String contentType) {
        Path target = resolveKey(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored file locally: {}", target);
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to store file locally: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream openStream(String key) throws IOException {
        Path target = resolveKey(key);
        if (!Files.exists(target)) throw new NotFoundException("File not found in local store: " + key);
        return Files.newInputStream(target);
    }

    @Override
    public InputStream openStream(String key, long start, long end) throws IOException {
        Path target = resolveKey(key);
        if (!Files.exists(target)) throw new NotFoundException("File not found in local store: " + key);
        InputStream base = Files.newInputStream(target);
        base.skipNBytes(start);
        return BoundedInputStream.builder().setInputStream(base).setMaxCount(end - start + 1).get();
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveKey(key));
            log.debug("Deleted local file: {}", resolveKey(key));
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to delete file from local store: " + e.getMessage(), e);
        }
    }

    private Path resolveKey(String key) {
        return Path.of(applicationConfig.localStoragePath()).resolve(key);
    }
}
