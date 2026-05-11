package de.merkeg.shawty.entry;

import de.merkeg.shawty.config.ApplicationConfig;
import de.merkeg.shawty.entry.rest.EntryInfo;
import de.merkeg.shawty.entry.rest.NewEntryRequest;
import de.merkeg.shawty.entry.rest.NewEntryResponse;
import de.merkeg.shawty.filestore.FileStore;
import de.merkeg.shawty.filestore.StoredFile;
import de.merkeg.shawty.user.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.UrlValidator;

import java.net.URI;
import java.net.URLConnection;
import java.util.Arrays;

@ApplicationScoped
public class EntryService {

    @Inject
    FileStore fileStore;

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

        String storageKey = entry.getId() + "." + extension;
        entry.setStorageKey(storageKey);
        entry.setFileSize(req.getFile().length());

        String contentType = URLConnection.guessContentTypeFromName(req.getFilename());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        fileStore.store(storageKey, req.getFile(), contentType);

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

    public StoredFile getEntryBytes(Entry entry) {
        return fileStore.get(entry.getStorageKey());
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

        if (entry.getStorageKey() != null) {
            fileStore.delete(entry.getStorageKey());
        }
        entry.delete();
    }

    public boolean isLinkPreview(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        String lower = userAgent.toLowerCase().trim();
        boolean matchesKnownAgents = Arrays.stream(PREVIEW_AGENTS).anyMatch(lower::contains);
        if (matchesKnownAgents) {
            return true;
        }
        return lower.contains("bot") || lower.contains("crawler") || lower.contains("spider") || lower.contains("preview");
    }

}
