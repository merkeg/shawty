package de.merkeg.shawty.entry;

import de.merkeg.shawty.entry.rest.EntryInfo;
import de.merkeg.shawty.entry.rest.NewEntryRequest;
import de.merkeg.shawty.entry.rest.NewEntryResponse;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

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
        Entry entry = entryService.createEntry(request);
        return RestResponse.ok(entryService.buildEntryResponse(entry));
    }

    @GET
    @Path("/{entryId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public RestResponse<byte[]> downloadEntry(@PathParam("entryId") String entryId, @QueryParam("download") boolean download, @HeaderParam(value = "User-Agent") String userAgent) {
        Entry entry = Entry.findById(entryId);

        if(entry == null) {
            throw new NotFoundException("File not found");
        }

        String dispositionType = DISPOSITION_INLINE;
        if(download || entryService.isLinkPreview(userAgent)) {
            dispositionType = DISPOSITION_ATTACHMENT;
        }

        ResponseBytes<GetObjectResponse> bytes = entryService.getEntryBytes(entry);
        return RestResponse.ResponseBuilder.ok(bytes.asByteArray())
                .header("Content-Disposition", dispositionType + "; filename=\"" + entry.getOriginalFilename() + "\"")
                .header("Content-Type", bytes.response().contentType()).build();
    }

    @DELETE
    @Path("/{entryId}")
    @Transactional
    public RestResponse<Void> deleteEntry(@PathParam("entryId") String entryId) {
        entryService.deleteEntry(entryId);
        return RestResponse.ok();
    }


}
