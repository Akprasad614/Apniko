package com.localcart.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_catalogue_source_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_catalogue_source_record",
                columnNames = {"source_name", "source_record_id"}),
        indexes = @Index(name = "idx_catalogue_source_product", columnList = "product_id"))
public class ProductCatalogueSourceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "source_name", nullable = false, length = 80)
    private String sourceName;

    @Column(name = "source_record_id", nullable = false, length = 160)
    private String sourceRecordId;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "source_license", nullable = false, length = 80)
    private String sourceLicense;

    @Column(name = "attribution", nullable = false, length = 500)
    private String attribution;

    @Column(name = "reuse_rights_verified")
    private Boolean reuseRightsVerified;

    @Column(name = "commercial_use_permitted")
    private Boolean commercialUsePermitted;

    @Column(name = "permission_reference", length = 500)
    private String permissionReference;

    @Column(name = "source_modified_at")
    private Instant sourceModifiedAt;

    @Column(name = "reported_mrp", precision = 12, scale = 2)
    private BigDecimal reportedMrp;

    @Column(name = "reported_mrp_source", length = 40)
    private String reportedMrpSource;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    // Raw Mendeley row metadata is retained for provenance/review, not exposed as shop price or customer nutrition facts.
    @Column(name = "source_metadata_json", columnDefinition = "TEXT")
    private String sourceMetadataJson;

    public ProductCatalogueSourceRecord() {
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceLicense() { return sourceLicense; }
    public void setSourceLicense(String sourceLicense) { this.sourceLicense = sourceLicense; }
    public String getAttribution() { return attribution; }
    public void setAttribution(String attribution) { this.attribution = attribution; }
    public Boolean getReuseRightsVerified() { return reuseRightsVerified; }
    public void setReuseRightsVerified(Boolean reuseRightsVerified) { this.reuseRightsVerified = reuseRightsVerified; }
    public Boolean getCommercialUsePermitted() { return commercialUsePermitted; }
    public void setCommercialUsePermitted(Boolean commercialUsePermitted) { this.commercialUsePermitted = commercialUsePermitted; }
    public String getPermissionReference() { return permissionReference; }
    public void setPermissionReference(String permissionReference) { this.permissionReference = permissionReference; }
    public Instant getSourceModifiedAt() { return sourceModifiedAt; }
    public void setSourceModifiedAt(Instant sourceModifiedAt) { this.sourceModifiedAt = sourceModifiedAt; }
    public BigDecimal getReportedMrp() { return reportedMrp; }
    public void setReportedMrp(BigDecimal reportedMrp) { this.reportedMrp = reportedMrp; }
    public String getReportedMrpSource() { return reportedMrpSource; }
    public void setReportedMrpSource(String reportedMrpSource) { this.reportedMrpSource = reportedMrpSource; }
    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
    public String getSourceMetadataJson() { return sourceMetadataJson; }
    public void setSourceMetadataJson(String sourceMetadataJson) { this.sourceMetadataJson = sourceMetadataJson; }
}
