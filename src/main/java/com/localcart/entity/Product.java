package com.localcart.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_brand_name", columnList = "brand,name"),
        @Index(name = "idx_products_category_subcategory", columnList = "category,subcategory"),
        @Index(name = "idx_products_catalogue_review_status", columnList = "catalogue_review_status")
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(max = 120, message = "Product name cannot exceed 120 characters")
    @Column(nullable = false, length = 120)
    private String name;

    @Size(max = 80, message = "Product category cannot exceed 80 characters")
    @Column(length = 80)
    private String category;

    @Size(max = 100)
    @Column(length = 100)
    private String subcategory;

    @Size(max = 100)
    @Column(length = 100)
    private String brand;

    @Size(max = 14)
    @Column(length = 14, unique = true)
    private String barcode;

    @Size(max = 80)
    @Column(name = "pack_size", length = 80)
    private String packSize;

    @Column(name = "net_quantity", precision = 12, scale = 3)
    private BigDecimal netQuantity;

    @Size(max = 20)
    @Column(name = "net_quantity_unit", length = 20)
    private String netQuantityUnit;

    @Size(max = 120)
    @Column(name = "variant_group_key", length = 120)
    private String variantGroupKey;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal mrp;

    @Size(max = 40)
    @Column(name = "mrp_source", length = 40)
    private String mrpSource;

    @Column(name = "mrp_verified_at")
    private Instant mrpVerifiedAt;

    @Size(max = 64)
    @Column(name = "identity_key", length = 64, unique = true)
    private String identityKey;

    @Size(max = 80)
    @Column(name = "catalogue_source", length = 80)
    private String catalogueSource;

    @Size(max = 80)
    @Column(name = "catalogue_source_license", length = 80)
    private String catalogueSourceLicense;

    @Column(name = "catalogue_imported_at")
    private Instant catalogueImportedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "catalogue_review_status", length = 24)
    private ProductCatalogueReviewStatus catalogueReviewStatus;

    @Size(max = 500)
    @Column(name = "catalogue_review_reason", length = 500)
    private String catalogueReviewReason;

    @Column(name = "catalogue_reviewed_at")
    private Instant catalogueReviewedAt;

    @Size(max = 120)
    @Column(name = "catalogue_reviewed_by", length = 120)
    private String catalogueReviewedBy;

    public Product() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getPackSize() { return packSize; }
    public void setPackSize(String packSize) { this.packSize = packSize; }
    public BigDecimal getNetQuantity() { return netQuantity; }
    public void setNetQuantity(BigDecimal netQuantity) { this.netQuantity = netQuantity; }
    public String getNetQuantityUnit() { return netQuantityUnit; }
    public void setNetQuantityUnit(String netQuantityUnit) { this.netQuantityUnit = netQuantityUnit; }
    public String getVariantGroupKey() { return variantGroupKey; }
    public void setVariantGroupKey(String variantGroupKey) { this.variantGroupKey = variantGroupKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
    public String getMrpSource() { return mrpSource; }
    public void setMrpSource(String mrpSource) { this.mrpSource = mrpSource; }
    public Instant getMrpVerifiedAt() { return mrpVerifiedAt; }
    public void setMrpVerifiedAt(Instant mrpVerifiedAt) { this.mrpVerifiedAt = mrpVerifiedAt; }
    public String getIdentityKey() { return identityKey; }
    public void setIdentityKey(String identityKey) { this.identityKey = identityKey; }
    public String getCatalogueSource() { return catalogueSource; }
    public void setCatalogueSource(String catalogueSource) { this.catalogueSource = catalogueSource; }
    public String getCatalogueSourceLicense() { return catalogueSourceLicense; }
    public void setCatalogueSourceLicense(String catalogueSourceLicense) { this.catalogueSourceLicense = catalogueSourceLicense; }
    public Instant getCatalogueImportedAt() { return catalogueImportedAt; }
    public void setCatalogueImportedAt(Instant catalogueImportedAt) { this.catalogueImportedAt = catalogueImportedAt; }
    public ProductCatalogueReviewStatus getCatalogueReviewStatus() { return catalogueReviewStatus; }
    public void setCatalogueReviewStatus(ProductCatalogueReviewStatus catalogueReviewStatus) { this.catalogueReviewStatus = catalogueReviewStatus; }
    public String getCatalogueReviewReason() { return catalogueReviewReason; }
    public void setCatalogueReviewReason(String catalogueReviewReason) { this.catalogueReviewReason = catalogueReviewReason; }
    public Instant getCatalogueReviewedAt() { return catalogueReviewedAt; }
    public void setCatalogueReviewedAt(Instant catalogueReviewedAt) { this.catalogueReviewedAt = catalogueReviewedAt; }
    public String getCatalogueReviewedBy() { return catalogueReviewedBy; }
    public void setCatalogueReviewedBy(String catalogueReviewedBy) { this.catalogueReviewedBy = catalogueReviewedBy; }

    @OneToOne(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ProductPhoto photo;

    @JsonIgnore
    public ProductPhoto getPhoto() { return photo; }
    public void setPhoto(ProductPhoto photo) { this.photo = photo; }

    /**
     * Safe, read-only photo projection for order entity responses.
     * The ProductPhoto relation itself is intentionally hidden because it also
     * contains pending/rejected candidate URLs and supplier evidence.
     */
    public String getImageUrl() {
        return photo != null ? photo.getApprovedImageUrl() : null;
    }

    public String getImageSourceName() {
        return getImageUrl() != null ? photo.getSourceName() : null;
    }

    public String getImageSourceUrl() {
        return getImageUrl() != null ? photo.getSourceUrl() : null;
    }

    public String getImageLicense() {
        return getImageUrl() != null ? photo.getLicense() : null;
    }

    public String getImageAttribution() {
        return getImageUrl() != null ? photo.getAttribution() : null;
    }

    public boolean isVisibleToCustomers() {
        return catalogueReviewStatus == null
                || catalogueReviewStatus == ProductCatalogueReviewStatus.ACTIVE;
    }
}
