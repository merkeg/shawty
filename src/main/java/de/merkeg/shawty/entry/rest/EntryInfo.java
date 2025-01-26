package de.merkeg.shawty.entry.rest;

import de.merkeg.shawty.entry.Entry;
import lombok.Data;
import org.mapstruct.Mapping;

@Data
public class EntryInfo {

    String id;
    String extension;
    String originalFilename;
    String uploaderId;

    @org.mapstruct.Mapper(componentModel = "cdi")
    public interface Mapper {
        @Mapping(target = "uploaderId", expression = "java(entry.getUploader().getId())")
        EntryInfo toDto(Entry entry);
    }
}
