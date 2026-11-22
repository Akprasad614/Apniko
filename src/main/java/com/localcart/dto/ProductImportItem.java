package com.localcart.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductImportItem(
        String name,
        String brand,
        String barcode,
        String packSize,
        BigDecimal netQuantity,
        String netQuantityUnit,
        String variantGroupKey,
        String description,
        String category,
        String subcategory,
        BigDecimal mrp,
        String mrpSource,
        String sourceRecordId,
        Instant sourceModifiedAt,
        String sourceMetadataJson,
        String identityDiscriminator,
        String sourceReviewReason
) {
}
