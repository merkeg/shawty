package de.merkeg.shawty.entry.rest;

import de.merkeg.shawty.entry.Entry;
import de.merkeg.shawty.entry.EntryType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.mapstruct.Mapping;

@Data
public class EntryInfo {

    @NotNull
    String id;

    String extension;
    String originalFilename;
    String uploaderId;
    EntryType type;
    String url;

    @org.mapstruct.Mapper(componentModel = "cdi")
    public interface Mapper {
        @Mapping(target = "uploaderId", expression = "java(entry.getUploader().getId())")
        EntryInfo toDto(Entry entry);
    }
}
