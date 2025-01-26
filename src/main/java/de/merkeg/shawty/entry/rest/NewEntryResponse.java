package de.merkeg.shawty.entry.rest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NewEntryResponse {

    String accessUrl;
    String deletionUrl;
    EntryInfo entry;
}
