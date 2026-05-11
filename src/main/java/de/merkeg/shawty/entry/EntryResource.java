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

    public static final String DISPOSITION_ATTACHMENT = "attachment";
    public static final String DISPOSITION_INLINE = "inline";

    @Inject
    EntryService entryService;

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
        NewEntryResponse response = entryService.buildEntryResponse(entry);
        return RestResponse.ok(response);
    }



    @GET
    @Path("/{entryId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public RestResponse<?> getEntry(
            @PathParam("entryId") @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID") String entryId,
            @QueryParam("download") boolean download,
            @HeaderParam(value = "User-Agent") String userAgent) {
        Entry entry = Entry.findById(entryId);

        if(entry == null) {
            throw new NotFoundException("File not found");
        }

        EntryType type = entry.getType();

        if(type == EntryType.FILE || type == null) {
            return downloadEntry(entry, download, userAgent);
        }

        if(type == EntryType.URL) {
            return redirectEntry(entry);
        }

        throw new InternalServerErrorException("Method not implemented.");
    }

    private RestResponse<byte[]> downloadEntry(Entry entry, boolean download, String userAgent) {
        String dispositionType = DISPOSITION_INLINE;
        if(download || entryService.isLinkPreview(userAgent)) {
            dispositionType = DISPOSITION_ATTACHMENT;
        }

        StoredFile storedFile = entryService.getEntryBytes(entry);
        return RestResponse.ResponseBuilder.ok(storedFile.content())
                .header("Content-Disposition", dispositionType + "; filename=\"" + entry.getOriginalFilename() + "\"")
                .header("Content-Type", storedFile.contentType()).build();
    }

    @SneakyThrows
    private RestResponse<Void> redirectEntry(Entry entry) {
        return RestResponse.seeOther(new URI(entry.getUrl()));
    }





    @DELETE
    @Path("/{entryId}")
    @Transactional
    public RestResponse<Void> deleteEntry(
            @PathParam("entryId") @Pattern(regexp = "[0-9A-Za-z]{1,22}", message = "Invalid entry ID") String entryId) {
        entryService.deleteEntry(entryId);
        return RestResponse.ok();
    }


}
