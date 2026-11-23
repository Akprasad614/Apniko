package com.localcart.dto;

import com.localcart.entity.ProductCatalogueReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CatalogueReviewUpdateRequest(
        @NotNull(message = "Catalogue review status is required")
        ProductCatalogueReviewStatus status,
        @Size(max = 500, message = "Review reason cannot exceed 500 characters")
        String reason
) {
}
