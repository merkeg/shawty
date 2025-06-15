package de.merkeg.shawty.entry.rest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NewUrlShortenRequest {
  @NotBlank
  String url;
}
