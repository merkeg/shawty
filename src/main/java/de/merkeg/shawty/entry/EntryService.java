package de.merkeg.shawty.entry;

import de.merkeg.shawty.config.ApplicationConfig;
import de.merkeg.shawty.entry.rest.EntryInfo;
import de.merkeg.shawty.entry.rest.NewEntryRequest;
import de.merkeg.shawty.entry.rest.NewEntryResponse;
import de.merkeg.shawty.filestore.FileStore;
import de.merkeg.shawty.filestore.StoredFile;
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
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.UrlValidator;

import java.net.URLConnection;

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

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public Entry createFileEntry(@Valid NewEntryRequest req) {

        String extension = FilenameUtils.getExtension(req.getFilename());
        String deleteKey = StringUtil.longUniqueText(1);

        Entry entry = Entry.builder()
                .originalFilename(req.getFilename())
                .extension(extension)
                .uploader((User) securityIdentity.getPrincipal())
                .type(EntryType.FILE)
                .deleteKeyHash(StringUtil.hashString(deleteKey))
                .build();

        entry.persist();

        String storageKey = entry.getId() + "." + extension;
        entry.setStorageKey(storageKey);
        entry.setFileSize(req.getFile().length());

        String contentType = URLConnection.guessContentTypeFromName(req.getFilename());
        if (contentType == null) contentType = "application/octet-stream";

        fileStore.store(storageKey, req.getFile(), contentType);

        return new EntryWithDeleteKey(entry, deleteKey);
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
        return new EntryWithDeleteKey(entry, deleteKey);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    public StoredFile getEntryBytes(Entry entry) {
        return fileStore.get(entry.getStorageKey());
    }

    public StoredFile getEntryBytesRange(Entry entry, long start, long end) {
        return fileStore.getRange(entry.getStorageKey(), start, end);
    }

    // ── Response builder ───────────────────────────────────────────────────────

    public NewEntryResponse buildEntryResponse(Entry entry) {
        String rawDeleteKey = null;
        if (entry instanceof EntryWithDeleteKey e) {
            rawDeleteKey = e.getRawDeleteKey();
        }

        EntryInfo info = entryInfoMapper.toDto(entry);

        String base = normalizeBaseUrl(applicationConfig.baseUrl());
        String pageUrl     = UriBuilder.fromUri(base).path(entry.getId()).build().toString();
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

    // ── Inner carrier ──────────────────────────────────────────────────────────

    /**
     * Thin wrapper that carries the plain-text delete key alongside the persisted
     * Entry – only used transiently in the same request before the response is built.
     */
    public static class EntryWithDeleteKey extends Entry {
        private final String rawDeleteKey;

        public EntryWithDeleteKey(Entry delegate, String rawDeleteKey) {
            this.setId(delegate.getId());
            this.setExtension(delegate.getExtension());
            this.setStorageKey(delegate.getStorageKey());
            this.setFileSize(delegate.getFileSize());
            this.setOriginalFilename(delegate.getOriginalFilename());
            this.setType(delegate.getType());
            this.setUrl(delegate.getUrl());
            this.setDeleteKeyHash(delegate.getDeleteKeyHash());
            this.setUploader(delegate.getUploader());
            this.rawDeleteKey = rawDeleteKey;
        }

        public String getRawDeleteKey() {
            return rawDeleteKey;
        }
    }
}
