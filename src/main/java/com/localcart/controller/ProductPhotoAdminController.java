package com.localcart.controller;

import com.localcart.dto.ProductPhotoCandidateRequest;
import com.localcart.dto.ProductPhotoImportTargetResponse;
import com.localcart.dto.ProductPhotoReviewRequest;
import com.localcart.dto.ProductPhotoReviewResponse;
import com.localcart.dto.ProductPhotoRolloutReportResponse;
import com.localcart.dto.ProductPhotoCandidateRetirementRequest;
import com.localcart.dto.ProductPhotoCandidateRetirementResponse;
import com.localcart.security.SecurityUtils;
import com.localcart.service.ProductPhotoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/catalogue")
@PreAuthorize("hasRole('ADMIN')")
public class ProductPhotoAdminController {
    private final ProductPhotoService photoService;

    public ProductPhotoAdminController(ProductPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/photo-review-queue")
    public ResponseEntity<Page<ProductPhotoReviewResponse>> getReviewQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(photoService.getReviewQueue(page, size));
    }

    @GetMapping("/photo-import-targets")
    public ResponseEntity<Page<ProductPhotoImportTargetResponse>> getImportTargets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(photoService.getImportTargets(page, size));
    }

    @GetMapping("/photo-rollout-report")
    public ResponseEntity<ProductPhotoRolloutReportResponse> getRolloutReport() {
        return ResponseEntity.ok(photoService.getRolloutReport());
    }

    @PostMapping("/photo-review-queue/retire-unverified")
    public ResponseEntity<ProductPhotoCandidateRetirementResponse> retireUnverifiedCandidates(
            @Valid @RequestBody ProductPhotoCandidateRetirementRequest request) {
        return ResponseEntity.ok(photoService.retirePendingCandidates(
                request.reason(), SecurityUtils.getCurrentUserEmail(), request.confirmRetirement()));
    }


    @PostMapping("/products/{productId}/photo-candidate")
    public ResponseEntity<ProductPhotoReviewResponse> submitCandidate(
            @PathVariable Long productId,
            @Valid @RequestBody ProductPhotoCandidateRequest request) {
        return ResponseEntity.ok(photoService.submitCandidate(productId, request));
    }

    @PatchMapping("/products/{productId}/photo-review")
    public ResponseEntity<ProductPhotoReviewResponse> reviewCandidate(
            @PathVariable Long productId,
            @Valid @RequestBody ProductPhotoReviewRequest request) {
        return ResponseEntity.ok(photoService.reviewCandidate(
                productId, request, SecurityUtils.getCurrentUserEmail()));
    }
}
