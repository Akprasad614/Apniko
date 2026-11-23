package com.localcart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductImportBatchRequest(
        @NotBlank @Size(max = 80) String sourceName,
        @NotBlank @Size(max = 500) String sourceUrl,
        @NotBlank @Size(max = 80) String sourceLicense,
        @NotBlank @Size(max = 500) String attribution,
        @NotEmpty @Size(max = 500) List<ProductImportItem> products
) {
}
