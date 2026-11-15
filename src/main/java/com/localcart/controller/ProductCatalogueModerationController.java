package com.localcart.controller;

import com.localcart.dto.CatalogueReviewItemResponse;
import com.localcart.dto.CatalogueReviewUpdateRequest;
import com.localcart.security.SecurityUtils;
import com.localcart.service.ProductCatalogueModerationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/catalogue")
@PreAuthorize("hasRole('ADMIN')")
public class ProductCatalogueModerationController {

    private final ProductCatalogueModerationService moderationService;

    public ProductCatalogueModerationController(
            ProductCatalogueModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/review-queue")
    public ResponseEntity<Page<CatalogueReviewItemResponse>> getReviewQueue(
            @RequestParam(required = false, defaultValue = "NEEDS_REVIEW") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(moderationService.getReviewQueue(status, page, size));
    }

    @PatchMapping("/products/{productId}/review")
    public ResponseEntity<CatalogueReviewItemResponse> updateReview(
            @PathVariable Long productId,
            @Valid @RequestBody CatalogueReviewUpdateRequest request) {
        return ResponseEntity.ok(moderationService.updateReview(
                productId, request, SecurityUtils.getCurrentUserEmail()));
    }
}
