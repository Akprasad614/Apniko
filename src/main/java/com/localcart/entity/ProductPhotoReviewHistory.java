package com.localcart.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Immutable snapshot of each submitted photograph at the point of review or retirement.
 * Retired candidates remain auditable without keeping them in the active review queue.
 */
@Entity
@Table(name = "product_photo_review_history",
        indexes = {
                @Index(name = "idx_photo_history_product", columnList = "product_id"),
                @Index(name = "idx_photo_history_reviewed_at", columnList = "reviewed_at")
        })
public class ProductPhotoReviewHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Size(max = 200)
    @Column(name = "product_name", length = 200)
    private String productName;

    @Size(max = 100)
    @Column(name = "product_brand", length = 100)
    private String productBrand;

    @Size(max = 14)
    @Column(name = "product_barcode", length = 14)
    private String productBarcode;

    @Size(max = 80)
    @Column(name = "product_pack_size", length = 80)
    private String productPackSize;

    @Size(max = 1000)
    @Column(name = "candidate_image_url", length = 1000)
    private String candidateImageUrl;

    @Size(max = 100)
    @Column(name = "candidate_source_name", length = 100)
    private String candidateSourceName;

    @Size(max = 500)
    @Column(name = "candidate_source_url", length = 500)
    private String candidateSourceUrl;

    @Size(max = 80)
    @Column(name = "candidate_license", length = 80)
    private String candidateLicense;

    @Size(max = 500)
    @Column(name = "candidate_attribution", length = 500)
    private String candidateAttribution;

    @Size(max = 200)
    @Column(name = "source_product_name", length = 200)
    private String sourceProductName;

    @Size(max = 100)
    @Column(name = "source_product_brand", length = 100)
    private String sourceProductBrand;

    @Size(max = 80)
    @Column(name = "source_pack_size", length = 80)
    private String sourcePackSize;

    @Size(max = 500)
    @Column(name = "authorization_reference", length = 500)
    private String authorizationReference;

    @Column(name = "candidate_submitted_at")
    private Instant candidateSubmittedAt;

    @Size(max = 16)
    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Size(max = 500)
    @Column(name = "reason", length = 500)
    private String reason;

    @Size(max = 120)
    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected ProductPhotoReviewHistory() {}

    public static ProductPhotoReviewHistory from(
            ProductPhoto photo, String outcome, String reason, String reviewedBy, Instant reviewedAt) {
        Product product = photo.getProduct();
        ProductPhotoReviewHistory history = new ProductPhotoReviewHistory();
        history.productId = product.getId();
        history.productName = product.getName();
        history.productBrand = product.getBrand();
        history.productBarcode = product.getBarcode();
        history.productPackSize = product.getPackSize();
        history.candidateImageUrl = photo.getCandidateImageUrl();
        history.candidateSourceName = photo.getCandidateSourceName();
        history.candidateSourceUrl = photo.getCandidateSourceUrl();
        history.candidateLicense = photo.getCandidateLicense();
        history.candidateAttribution = photo.getCandidateAttribution();
        history.sourceProductName = photo.getCandidateProductName();
        history.sourceProductBrand = photo.getCandidateProductBrand();
        history.sourcePackSize = photo.getCandidatePackSize();
        history.authorizationReference = photo.getCandidateAuthorizationReference();
        history.candidateSubmittedAt = photo.getSubmittedAt();
        history.outcome = outcome;
        history.reason = reason;
        history.reviewedBy = reviewedBy;
        history.reviewedAt = reviewedAt;
        return history;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public Instant getReviewedAt() { return reviewedAt; }
}
