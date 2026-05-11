package de.merkeg.shawty.entry;

import de.merkeg.shawty.entry.rest.NewEntryRequest;
import de.merkeg.shawty.entry.rest.NewEntryResponse;
import de.merkeg.shawty.entry.rest.NewUrlShortenRequest;
import de.merkeg.shawty.filestore.StoredFile;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;

@Path("/")
@Slf4j
public class EntryResource {

    @Inject
    EntryService entryService;

    @Inject
    EntryHtmlService entryHtmlService;

    // ── Upload endpoints ───────────────────────────────────────────────────────

    @POST
    @Path("/api/entries")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("uploader")
    public RestResponse<NewEntryResponse> uploadEntry(@Valid NewEntryRequest request) {
        Entry entry = entryService.createFileEntry(request);
        return RestResponse.ok(entryService.buildEntryResponse(entry));
    }

    @POST
    @Path("/api/entries/url")
    @RolesAllowed("uploader")
    public RestResponse<NewEntryResponse> shortenLink(NewUrlShortenRequest request) {
        Entry entry = entryService.createUrlEntry(request.getUrl());
        return RestResponse.ok(entryService.buildEntryResponse(entry));
    }

    // ── Public access endpoints ────────────────────────────────────────────────

    /**
     * Returns an HTML preview page for the entry.
     * If {@code ?download=true} is set, the raw file is returned as a download attachment.
     */
    @GET
    @Path("/{entryId}")
    @Transactional
    public RestResponse<?> getEntry(
            @PathParam("entryId") @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID") String entryId,
            @QueryParam("download") boolean download) {

        Entry entry = Entry.findById(entryId);
        if (entry == null) {
            return RestResponse.ResponseBuilder
                    .create(RestResponse.Status.NOT_FOUND, entryHtmlService.buildNotFoundPage())
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .build();
        }

        if (entry.getType() == EntryType.URL) {
            return redirectEntry(entry);
        }

        if (download) {
            return serveFile(entry, "attachment");
        }

        String html = entryHtmlService.buildPage(entry);
        return RestResponse.ResponseBuilder
                .ok(html)
                .header("Content-Type", "text/html; charset=UTF-8")
                .build();
    }

    /**
     * Serves the raw file inline – used by the HTML preview page for embedding
     * (img src, video src, audio src, PDF embed).
     */
    @GET
    @Path("/{entryId}/raw")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public RestResponse<byte[]> getRawFile(
            @PathParam("entryId") @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID") String entryId) {

        Entry entry = Entry.findById(entryId);
        if (entry == null) throw new NotFoundException("File not found");
        return serveFile(entry, "inline");
    }

    // ── Delete endpoints ───────────────────────────────────────────────────────

    /**
     * Authenticated delete: uploader can delete own entries, admin can delete any entry.
     */
    @DELETE
    @Path("/api/entries/{entryId}")
    @Transactional
    @RolesAllowed({"uploader", "admin"})
    public RestResponse<Void> deleteEntry(
            @PathParam("entryId") @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID") String entryId) {
        entryService.deleteEntry(entryId);
        return RestResponse.ok();
    }

    /**
     * Public delete via delete key – no authentication required.
     * The delete key is returned when the entry was created.
     */
    @GET
    @Path("/{entryId}/{deleteKey}")
    @Transactional
    public RestResponse<Void> deleteEntryByKey(
            @PathParam("entryId")   @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID")   String entryId,
            @PathParam("deleteKey") @Pattern(regexp = "[0-9A-Za-z]{1,44}", message = "Invalid delete key") String deleteKey) {
        entryService.deleteEntryByKey(entryId, deleteKey);
        return RestResponse.noContent();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private RestResponse<byte[]> serveFile(Entry entry, String dispositionType) {
        StoredFile storedFile = entryService.getEntryBytes(entry);
        String contentType = storedFile.contentType();
        if (contentType != null && contentType.startsWith("text/") && !contentType.contains("charset")) {
            contentType = contentType + "; charset=UTF-8";
        }
        return RestResponse.ResponseBuilder.ok(storedFile.content())
                .header("Content-Disposition", dispositionType + "; filename=\"" + entry.getOriginalFilename() + "\"")
                .header("Content-Type", contentType)
                .build();
    }

    @SneakyThrows
    private RestResponse<Void> redirectEntry(Entry entry) {
        return RestResponse.seeOther(new URI(entry.getUrl()));
    }
}
