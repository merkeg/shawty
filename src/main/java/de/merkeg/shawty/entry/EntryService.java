package de.merkeg.shawty.entry;

import de.merkeg.shawty.config.ApplicationConfig;
import de.merkeg.shawty.entry.rest.EntryInfo;
import de.merkeg.shawty.entry.rest.NewEntryRequest;
import de.merkeg.shawty.entry.rest.NewEntryResponse;
import de.merkeg.shawty.user.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.UrlValidator;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.net.URLConnection;
import java.util.Arrays;

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

    private static final String[] PREVIEW_AGENTS = {
            "whatsapp",
            "telegram",
            "signal",
            "twitter",
            "facebook",
            "linkedinbot",
            "slackbot",
            "discordbot",
            "googlebot",
            "bingbot",
            "applebot",
            "yahoo",
            "pinterest",
            "embedly",
            "quora link preview",
            "outbrain",
            "facebookexternalhit",
            "facebot",
            "ia_archiver"
    };


    @Transactional
    public Entry createFileEntry(@Valid NewEntryRequest req) {

        String extension = FilenameUtils.getExtension(req.getFilename());

        Entry entry = Entry.builder()
                .originalFilename(req.getFilename())
                .extension(extension)
                .uploader((User) securityIdentity.getPrincipal())
                .type(EntryType.FILE)
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

    @Transactional
    public Entry createUrlEntry(String url) {
        UrlValidator urlValidator = new UrlValidator();

        if(!urlValidator.isValid(url)) {
            throw new BadRequestException("Invalid URL");
        }

        Entry entry = Entry.builder()
                .url(url)
                .uploader((User) securityIdentity.getPrincipal())
                .type(EntryType.URL)
                .build();

        entry.persist();
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

    public boolean isLinkPreview(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        String lower = userAgent.toLowerCase().trim();
        // Prüfe auf bekannte Preview Agenten
        boolean matchesKnownAgents = Arrays.stream(PREVIEW_AGENTS).anyMatch(lower::contains);
        if (matchesKnownAgents) {
            return true;
        }
        // Prüfe allgemein auf typische Crawler/Spider/Bot Keywords
        return lower.contains("bot") || lower.contains("crawler") || lower.contains("spider") || lower.contains("preview");
    }

}
