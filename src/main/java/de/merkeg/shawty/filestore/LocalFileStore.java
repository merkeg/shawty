package de.merkeg.shawty.filestore;

import de.merkeg.shawty.config.ApplicationConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLConnection;
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
    public StoredFile get(String key) {
        Path target = resolveKey(key);
        if (!Files.exists(target)) {
            throw new NotFoundException("File not found in local store: " + key);
        }
        try {
            byte[] content = Files.readAllBytes(target);
            return new StoredFile(content, guessContentType(key));
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to read file from local store: " + e.getMessage(), e);
        }
    }

    @Override
    public StoredFile getRange(String key, long start, long end) {
        Path target = resolveKey(key);
        if (!Files.exists(target)) {
            throw new NotFoundException("File not found in local store: " + key);
        }
        try (RandomAccessFile raf = new RandomAccessFile(target.toFile(), "r")) {
            long length = end - start + 1;
            byte[] buffer = new byte[(int) length];
            raf.seek(start);
            raf.readFully(buffer);
            return new StoredFile(buffer, guessContentType(key));
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to read file range from local store: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        Path target = resolveKey(key);
        try {
            Files.deleteIfExists(target);
            log.debug("Deleted local file: {}", target);
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to delete file from local store: " + e.getMessage(), e);
        }
    }

    private Path resolveKey(String key) {
        return Path.of(applicationConfig.localStoragePath()).resolve(key);
    }

    private String guessContentType(String key) {
        String ct = URLConnection.guessContentTypeFromName(key);
        return ct != null ? ct : "application/octet-stream";
    }
}
