package com.localcart.dto;

import com.localcart.entity.ProductImageReviewStatus;
import java.time.Instant;

public record ProductPhotoReviewResponse(
        Long productId,
        String productName,
        String brand,
        String barcode,
        String packSize,
        String sourceProductName,
        String sourceProductBrand,
        String sourcePackSize,
        String candidateAuthorizationReference,
        String candidateImageUrl,
        String candidateSourceName,
        String candidateSourceUrl,
        String candidateLicense,
        String candidateAttribution,
        String approvedImageUrl,
        ProductImageReviewStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        String reviewedBy,
        String reviewReason
) {
}
