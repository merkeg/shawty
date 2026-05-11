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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestResponse;

import java.io.InputStream;
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
     * Serves the raw file inline – supports HTTP Range requests for video/audio streaming.
     */
    @GET
    @Path("/{entryId}/raw")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public Response getRawFile(
            @PathParam("entryId") @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID") String entryId,
            @HeaderParam("Range") String rangeHeader) {

        Entry entry = Entry.findById(entryId);
        if (entry == null) throw new NotFoundException("File not found");

        String disposition = "inline; filename=\"" + entry.getOriginalFilename() + "\"";
        long totalSize = entry.getFileSize() != null ? entry.getFileSize() : -1;

        // ── Range request (partial content) ───────────────────────────────────
        if (rangeHeader != null && rangeHeader.startsWith("bytes=") && totalSize > 0) {
            try {
                String rangeSpec = rangeHeader.substring(6);
                String firstRange = rangeSpec.split(",")[0].trim();

                long start, end;
                if (firstRange.startsWith("-")) {
                    long suffix = Long.parseLong(firstRange.substring(1));
                    start = Math.max(0, totalSize - suffix);
                    end = totalSize - 1;
                } else if (firstRange.endsWith("-")) {
                    start = Long.parseLong(firstRange.substring(0, firstRange.length() - 1));
                    end = totalSize - 1;
                } else {
                    String[] parts = firstRange.split("-");
                    start = Long.parseLong(parts[0]);
                    end = Long.parseLong(parts[1]);
                }

                end = Math.min(end, totalSize - 1);
                if (start > end || start < 0) {
                    return Response.status(416).header("Content-Range", "bytes */" + totalSize).build();
                }

                final long rangeStart = start;
                final long rangeEnd = end;
                String contentType = resolveContentType(entryService.getEntryBytes(entry).contentType());
                StreamingOutput stream = output -> {
                    try (InputStream in = entryService.openEntryStream(entry, rangeStart, rangeEnd)) {
                        in.transferTo(output);
                    }
                };

                return Response.status(206)
                        .header("Content-Type", contentType)
                        .header("Content-Range", "bytes " + start + "-" + end + "/" + totalSize)
                        .header("Content-Length", end - start + 1)
                        .header("Accept-Ranges", "bytes")
                        .header("Content-Disposition", disposition)
                        .entity(stream)
                        .build();

            } catch (NumberFormatException e) {
                log.debug("Unparseable Range header: {}", rangeHeader);
            }
        }

        // ── Full streaming response ────────────────────────────────────────────
        StoredFile meta = entryService.getEntryBytes(entry);
        String contentType = resolveContentType(meta.contentType());
        StreamingOutput stream = output -> {
            try (InputStream in = entryService.openEntryStream(entry)) {
                in.transferTo(output);
            }
        };

        Response.ResponseBuilder builder = Response.ok(stream)
                .header("Content-Type", contentType)
                .header("Accept-Ranges", "bytes")
                .header("Content-Disposition", disposition);

        if (totalSize > 0) builder.header("Content-Length", totalSize);

        return builder.build();
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
        String contentType = resolveContentType(storedFile.contentType());
        return RestResponse.ResponseBuilder.ok(storedFile.content())
                .header("Content-Disposition", dispositionType + "; filename=\"" + entry.getOriginalFilename() + "\"")
                .header("Content-Type", contentType)
                .build();
    }

    private String resolveContentType(String contentType) {
        if (contentType != null && contentType.startsWith("text/") && !contentType.contains("charset")) {
            return contentType + "; charset=UTF-8";
        }
        return contentType;
    }

    @SneakyThrows
    private RestResponse<Void> redirectEntry(Entry entry) {
        return RestResponse.seeOther(new URI(entry.getUrl()));
    }
}
