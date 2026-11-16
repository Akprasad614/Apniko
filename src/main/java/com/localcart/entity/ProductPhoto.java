package com.localcart.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Entity
@Table(name = "product_photos",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_photo_product", columnNames = "product_id"),
        indexes = @Index(name = "idx_product_photo_review_status", columnList = "review_status"))
public class ProductPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Size(max = 1000)
    @Column(name = "approved_image_url", length = 1000)
    private String approvedImageUrl;

    @Size(max = 100)
    @Column(name = "source_name", length = 100)
    private String sourceName;

    @Size(max = 500)
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Size(max = 80)
    @Column(name = "image_license", length = 80)
    private String license;

    @Size(max = 500)
    @Column(name = "image_attribution", length = 500)
    private String attribution;

    @Size(max = 500)
    @Column(name = "authorization_reference", length = 500)
    private String authorizationReference;


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
    @Column(name = "candidate_product_name", length = 200)
    private String candidateProductName;

    @Size(max = 100)
    @Column(name = "candidate_product_brand", length = 100)
    private String candidateProductBrand;

    @Size(max = 80)
    @Column(name = "candidate_pack_size", length = 80)
    private String candidatePackSize;

    @Size(max = 500)
    @Column(name = "candidate_authorization_reference", length = 500)
    private String candidateAuthorizationReference;


    @Size(max = 14)
    @Column(name = "candidate_barcode", length = 14)
    private String candidateBarcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 16)
    private ProductImageReviewStatus reviewStatus;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Size(max = 120)
    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Size(max = 500)
    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getApprovedImageUrl() { return approvedImageUrl; }
    public void setApprovedImageUrl(String value) { this.approvedImageUrl = value; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String value) { this.sourceName = value; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { this.sourceUrl = value; }
    public String getLicense() { return license; }
    public void setLicense(String value) { this.license = value; }
    public String getAttribution() { return attribution; }
    public void setAttribution(String value) { this.attribution = value; }
    public String getAuthorizationReference() { return authorizationReference; }
    public void setAuthorizationReference(String value) { this.authorizationReference = value; }
    public String getCandidateImageUrl() { return candidateImageUrl; }
    public void setCandidateImageUrl(String value) { this.candidateImageUrl = value; }
    public String getCandidateSourceName() { return candidateSourceName; }
    public void setCandidateSourceName(String value) { this.candidateSourceName = value; }
    public String getCandidateSourceUrl() { return candidateSourceUrl; }
    public void setCandidateSourceUrl(String value) { this.candidateSourceUrl = value; }
    public String getCandidateLicense() { return candidateLicense; }
    public void setCandidateLicense(String value) { this.candidateLicense = value; }
    public String getCandidateAttribution() { return candidateAttribution; }
    public void setCandidateAttribution(String value) { this.candidateAttribution = value; }
    public String getCandidateProductName() { return candidateProductName; }
    public void setCandidateProductName(String value) { this.candidateProductName = value; }
    public String getCandidateProductBrand() { return candidateProductBrand; }
    public void setCandidateProductBrand(String value) { this.candidateProductBrand = value; }
    public String getCandidatePackSize() { return candidatePackSize; }
    public void setCandidatePackSize(String value) { this.candidatePackSize = value; }
    public String getCandidateAuthorizationReference() { return candidateAuthorizationReference; }
    public void setCandidateAuthorizationReference(String value) { this.candidateAuthorizationReference = value; }
    public String getCandidateBarcode() { return candidateBarcode; }
    public void setCandidateBarcode(String value) { this.candidateBarcode = value; }
    public ProductImageReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ProductImageReviewStatus value) { this.reviewStatus = value; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant value) { this.submittedAt = value; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant value) { this.reviewedAt = value; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String value) { this.reviewedBy = value; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String value) { this.reviewReason = value; }

    public void clearCandidate() {
        candidateImageUrl = null;
        candidateSourceName = null;
        candidateSourceUrl = null;
        candidateLicense = null;
        candidateAttribution = null;
        candidateProductName = null;
        candidateProductBrand = null;
        candidatePackSize = null;
        candidateAuthorizationReference = null;
        candidateBarcode = null;
    }
}
