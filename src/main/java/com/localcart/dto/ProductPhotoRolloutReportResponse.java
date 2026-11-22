package com.localcart.dto;

public record ProductPhotoRolloutReportResponse(
        long totalCatalogueProducts,
        long productsWithBarcodeField,
        long productsWithoutApprovedPhoto,
        long productsWithApprovedPhoto,
        long pendingCandidates,
        long rejectedCandidates,
        long approvedManufacturerAuthorizedPhotos,
        long productsEligibleForFreshLookup,
        long retiredCandidates
) {}
