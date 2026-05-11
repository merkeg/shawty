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
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(key)
                .build();
        var response = s3Client.getObjectAsBytes(request);
        return new StoredFile(response.asByteArray(), response.response().contentType());
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(key)
                .build();
        s3Client.deleteObject(request);
    }
}

