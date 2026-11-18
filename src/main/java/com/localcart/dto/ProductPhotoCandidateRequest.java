package com.localcart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductPhotoCandidateRequest(
        @NotBlank @Size(max = 1000) String imageUrl,
        @NotBlank @Size(max = 500) String sourceUrl,
        @NotBlank @Size(max = 100) String sourceName,
        @NotBlank @Size(max = 200) String sourceProductName,
        @Size(max = 100) String sourceProductBrand,
        @Size(max = 80) String sourcePackSize,
        @NotBlank @Size(max = 80) String license,
        @NotBlank @Size(max = 500) String attribution,
        @Size(max = 500) String authorizationReference,
        @NotBlank @Size(max = 14) String matchedBarcode
) {
}
