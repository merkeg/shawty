package de.merkeg.shawty.entry;

import de.merkeg.shawty.config.ApplicationConfig;
import de.merkeg.shawty.entry.rest.EntryInfo;
import de.merkeg.shawty.entry.rest.NewEntryRequest;
import de.merkeg.shawty.entry.rest.NewEntryResponse;
import de.merkeg.shawty.user.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.io.FilenameUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;

@ApplicationScoped
public class EntryService {

    @Inject
    S3Client s3Client;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    EntryInfo.Mapper entryInfoMapper;


    @Transactional
    public Entry createEntry(@Valid NewEntryRequest req) {

        String extension = FilenameUtils.getExtension(req.getFilename());

        Entry entry = Entry.builder()
                .originalFilename(req.getFilename())
                .extension(extension)
                .uploader((User) securityIdentity.getPrincipal())
                .build();

        entry.persist();

        String s3Key = entry.getId() + "." + extension;
        entry.setS3Key(s3Key);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(s3Key)
                .contentType(URLConnection.guessContentTypeFromName(req.getFilename()))
                .build();
        PutObjectResponse response = s3Client.putObject(request, RequestBody.fromFile(req.getFile()));

        if(response == null) {
            throw new InternalServerErrorException("Failed uploading file");
        }
        return entry;

    }

    public ResponseBytes<GetObjectResponse> getEntryBytes(Entry entry) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(entry.getS3Key())
                .build();

        return s3Client.getObjectAsBytes(request);
    }

    public NewEntryResponse buildEntryResponse(Entry entry) {
        EntryInfo info = entryInfoMapper.toDto(entry);
        String accessUrl = appendUrl(applicationConfig.baseUrl(), entry.getId());
        String deletionUrl = appendUrl(applicationConfig.baseUrl(), entry.getId());
        return NewEntryResponse.builder()
                .entry(info)
                .accessUrl(accessUrl)
                .deletionUrl(deletionUrl)
                .build();
    }

    private String appendUrl(String baseUrl, String appendage) {
        URI uri = URI.create(baseUrl);
        return uri.resolve(appendage).toString();
    }

    public void deleteEntry(String entryId) {
        Entry entry = Entry.findById(entryId);
        if(entry == null) {
            throw new NotFoundException("Entry not found");
        }

        entry.delete();
        DeleteObjectRequest req = DeleteObjectRequest.builder()
                .bucket(applicationConfig.bucket())
                .key(entry.getS3Key()).build();

        s3Client.deleteObject(req);

    }

    @RequestScoped
    public boolean isLinkPreview(String userAgent) {
        if(userAgent.toLowerCase().contains("whatsapp")) {
            return true;
        }

        if(userAgent.toLowerCase().contains("telegram")) {
            return true;
        }
        return false;
    }

}
