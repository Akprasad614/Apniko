package com.localcart.dto;

import com.localcart.entity.ProductCatalogueReviewStatus;

import java.time.Instant;

public record CatalogueReviewItemResponse(
        Long id,
        String name,
        String brand,
        String barcode,
        String packSize,
        String category,
        String subcategory,
        String catalogueSource,
        ProductCatalogueReviewStatus status,
        String reviewReason,
        Instant importedAt,
        Instant reviewedAt,
        String reviewedBy
) {
}
