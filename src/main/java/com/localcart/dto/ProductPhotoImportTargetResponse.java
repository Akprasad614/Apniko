package com.localcart.dto;

public record ProductPhotoImportTargetResponse(
        Long productId,
        String name,
        String brand,
        String barcode,
        String packSize
) {
}
