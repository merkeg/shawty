package de.merkeg.shawty.filestore;

import de.merkeg.shawty.config.ApplicationConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@ApplicationScoped
@S3Storage
public class S3FileStore implements FileStore {

    @Inject
    S3Client s3Client;

    @Inject
    ApplicationConfig applicationConfig;

    @Override
    public void store(String key, File file, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromFile(file));
    }

    @Override
    public StoredFile get(String key) {
        var response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(key)
                .build());
        return new StoredFile(response.asByteArray(), response.response().contentType());
    }

    @Override
    public InputStream openStream(String key) throws IOException {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(key)
                .build());
    }

    @Override
    public InputStream openStream(String key, long start, long end) throws IOException {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(key)
                .range("bytes=" + start + "-" + end)
                .build());
    }

    @Override
    public StoredFile getRange(String key, long start, long end) {
        var response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(key)
                .range("bytes=" + start + "-" + end)
                .build());
        return new StoredFile(response.asByteArray(), response.response().contentType());
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(key)
                .build());
    }
}
