package de.merkeg.shawty.entry.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.MediaType;
import lombok.Data;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.File;

@Data
public class NewEntryRequest {

    @RestForm("file")
    @NotNull
    private File file;

    @RestForm
    @PartType(MediaType.TEXT_PLAIN)
    @NotBlank
    public String filename;

}
