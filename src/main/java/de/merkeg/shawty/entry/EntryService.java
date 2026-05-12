package de.merkeg.shawty.entry;

import de.merkeg.shawty.config.ApplicationConfig;
import de.merkeg.shawty.entry.rest.EntryInfo;
import de.merkeg.shawty.entry.rest.NewEntryRequest;
import de.merkeg.shawty.entry.rest.NewEntryResponse;
import de.merkeg.shawty.filestore.FileStore;
import de.merkeg.shawty.user.Role;
import de.merkeg.shawty.user.User;
import de.merkeg.shawty.util.StringUtil;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.UrlValidator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;

@ApplicationScoped
@Slf4j
public class EntryService {

    @Inject
    FileStore fileStore;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    EntryInfo.Mapper entryInfoMapper;

    @Inject
    EntryMapper entryMapper;

    @Inject
    ArchiveListingService archiveListingService;

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public Entry createFileEntry(@Valid NewEntryRequest req) {
        String extension   = FilenameUtils.getExtension(req.getFilename());
        String deleteKey   = StringUtil.longUniqueText(1);
        String contentType = URLConnection.guessContentTypeFromName(req.getFilename());
        if (contentType == null) contentType = "application/octet-stream";

        Entry entry = Entry.builder()
                .originalFilename(req.getFilename())
                .extension(extension)
                .uploader((User) securityIdentity.getPrincipal())
                .type(EntryType.FILE)
                .deleteKeyHash(StringUtil.hashString(deleteKey))
                .contentType(contentType)
                .build();

        entry.persist();

        String storageKey = entry.getId() + "." + extension;
        entry.setStorageKey(storageKey);
        entry.setFileSize(req.getFile().length());

        fileStore.store(storageKey, req.getFile(), contentType);

        // ── Archive listing ────────────────────────────────────────────────────
        if (isArchiveFilename(req.getFilename())) {
            try (InputStream is = new FileInputStream(req.getFile())) {
                List<String> archiveEntries =
                        archiveListingService.listFirstLevelEntries(is, req.getFilename());
                entry.setArchiveEntries(archiveEntries);
            } catch (Exception e) {
                log.warn("Could not index archive entries for '{}': {}", req.getFilename(), e.getMessage());
            }
        }

        EntryWithDeleteKey result = entryMapper.copyToDeleteKeyEntry(entry);
        result.setRawDeleteKey(deleteKey);
        return result;
    }

    @Transactional
    public Entry createUrlEntry(String url) {
        UrlValidator urlValidator = new UrlValidator();
        if (!urlValidator.isValid(url)) throw new BadRequestException("Invalid URL");

        String deleteKey = StringUtil.longUniqueText(1);

        Entry entry = Entry.builder()
                .url(url)
                .uploader((User) securityIdentity.getPrincipal())
                .type(EntryType.URL)
                .deleteKeyHash(StringUtil.hashString(deleteKey))
                .build();

        entry.persist();

        EntryWithDeleteKey result = entryMapper.copyToDeleteKeyEntry(entry);
        result.setRawDeleteKey(deleteKey);
        return result;
    }

    // ── Read (streaming only) ──────────────────────────────────────────────────

    public InputStream openEntryStream(Entry entry) {
        try {
            return fileStore.openStream(entry.getStorageKey());
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to open stream: " + e.getMessage(), e);
        }
    }

    public InputStream openEntryStream(Entry entry, long start, long end) {
        try {
            return fileStore.openStream(entry.getStorageKey(), start, end);
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to open stream range: " + e.getMessage(), e);
        }
    }

    // ── Response builder ───────────────────────────────────────────────────────

    public NewEntryResponse buildEntryResponse(Entry entry) {
        String rawDeleteKey = null;
        if (entry instanceof EntryWithDeleteKey e) {
            rawDeleteKey = e.getRawDeleteKey();
        }

        EntryInfo info = entryInfoMapper.toDto(entry);

        String base    = normalizeBaseUrl(applicationConfig.baseUrl());
        String pageUrl = UriBuilder.fromUri(base).path(entry.getId()).build().toString();
        String deletionUrl = rawDeleteKey != null
                ? UriBuilder.fromUri(base).path(entry.getId()).path(rawDeleteKey).build().toString()
                : null;

        return NewEntryResponse.builder()
                .entry(info)
                .accessUrl(pageUrl)
                .deletionUrl(deletionUrl)
                .deleteKey(rawDeleteKey)
                .build();
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    /** Authenticated delete – uploader can only delete own entries, admin can delete any. */
    @Transactional
    public void deleteEntry(String entryId) {
        Entry entry = Entry.findById(entryId);
        if (entry == null) throw new NotFoundException("Entry not found");

        User currentUser = (User) securityIdentity.getPrincipal();
        boolean isAdmin  = securityIdentity.hasRole(Role.admin.name());
        boolean isOwner  = entry.getUploader() != null
                && entry.getUploader().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("You are not allowed to delete this entry");
        }

        performDelete(entry);
    }

    /** Public delete via delete-key in URL. */
    @Transactional
    public void deleteEntryByKey(String entryId, String deleteKey) {
        Entry entry = Entry.findById(entryId);
        if (entry == null) throw new NotFoundException("Entry not found");

        String expectedHash = StringUtil.hashString(deleteKey);
        if (entry.getDeleteKeyHash() == null || !entry.getDeleteKeyHash().equals(expectedHash)) {
            throw new ForbiddenException("Invalid delete key");
        }

        performDelete(entry);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void performDelete(Entry entry) {
        if (entry.getStorageKey() != null) {
            fileStore.delete(entry.getStorageKey());
        }
        entry.delete();
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /**
     * Returns {@code true} for archive extensions that {@link ArchiveListingService} can read.
     * Other archive types (.rar, .7z) are not listed but still show the archive preview template.
     */
    static boolean isArchiveFilename(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".zip")
                || lower.endsWith(".tar")
                || lower.endsWith(".tar.gz")
                || lower.endsWith(".tgz");
    }

    // ── Inner carrier ──────────────────────────────────────────────────────────

    /**
     * Thin wrapper that carries the plaintext delete key alongside the persisted Entry.
     * Only used transiently within the same request before the response is built.
     * Fields are copied from the delegate {@link Entry} via {@link EntryMapper}.
     */
    public static class EntryWithDeleteKey extends Entry {
        @lombok.Getter
        @lombok.Setter
        private String rawDeleteKey;
    }
}
