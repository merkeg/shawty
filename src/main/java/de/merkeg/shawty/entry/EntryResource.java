package de.merkeg.shawty.entry;

import de.merkeg.shawty.entry.rest.NewEntryRequest;
import de.merkeg.shawty.entry.rest.NewEntryResponse;
import de.merkeg.shawty.entry.rest.NewUrlShortenRequest;
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
import java.net.URLConnection;

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
     */
    @GET
    @Path("/{entryId}")
    @Transactional
    public Response getEntry(
            @PathParam("entryId") @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID") String entryId) {

        Entry entry = Entry.findById(entryId);
        if (entry == null) {
            String html = entryHtmlService.buildNotFoundPage();
            return Response.status(404).entity(html).header("Content-Type", "text/html; charset=UTF-8").build();
        }

        if (entry.getType() == EntryType.URL) {
            return redirectEntry(entry).toResponse();
        }

        String html = entryHtmlService.buildPage(entry);
        return Response.ok(html).header("Content-Type", "text/html; charset=UTF-8").build();
    }

    /**
     * Serves the file as a download attachment.
     */
    @GET
    @Path("/{entryId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public Response downloadFile(
            @PathParam("entryId") @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID") String entryId) {

        Entry entry = Entry.findById(entryId);
        if (entry == null) throw new NotFoundException("File not found");
        if (entry.getType() == EntryType.URL) throw new BadRequestException("This entry is a URL redirect, not a file");
        return serveFile(entry, "attachment");
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
        if (entry.getType() == EntryType.URL) throw new BadRequestException("This entry is a URL redirect, not a file");

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
                StreamingOutput stream = output -> {
                    try (InputStream in = entryService.openEntryStream(entry, rangeStart, rangeEnd)) {
                        in.transferTo(output);
                    }
                };

                return Response.status(206)
                        .header("Content-Type", resolveContentType(entry))
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
        StreamingOutput stream = output -> {
            try (InputStream in = entryService.openEntryStream(entry)) {
                in.transferTo(output);
            }
        };

        Response.ResponseBuilder builder = Response.ok(stream)
                .header("Content-Type", resolveContentType(entry))
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

    private Response serveFile(Entry entry, String dispositionType) {
        StreamingOutput stream = output -> {
            try (InputStream in = entryService.openEntryStream(entry)) {
                in.transferTo(output);
            }
        };
        return Response.ok(stream)
                .header("Content-Type", resolveContentType(entry))
                .header("Content-Disposition", dispositionType + "; filename=\"" + entry.getOriginalFilename() + "\"")
                .header("Content-Length", entry.getFileSize())
                .build();
    }

    private String resolveContentType(Entry entry) {
        String ct = entry.getContentType();
        if (ct == null || ct.isBlank()) {
            ct = URLConnection.guessContentTypeFromName(entry.getOriginalFilename());
        }
        if (ct == null) ct = "application/octet-stream";
        if (ct.startsWith("text/") && !ct.contains("charset")) ct += "; charset=UTF-8";
        return ct;
    }

    @SneakyThrows
    private RestResponse<Void> redirectEntry(Entry entry) {
        return RestResponse.seeOther(new URI(entry.getUrl()));
    }
}
