package com.localcart.dto;

import com.localcart.entity.ProductImageReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductPhotoReviewRequest(
        @NotNull ProductImageReviewStatus status,
        @Size(max = 500) String reason,
        boolean verificationConfirmed
) {
}
