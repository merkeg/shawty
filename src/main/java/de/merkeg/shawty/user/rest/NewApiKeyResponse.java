package de.merkeg.shawty.user.rest;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class NewApiKeyResponse {
    private String apiKey;
}
