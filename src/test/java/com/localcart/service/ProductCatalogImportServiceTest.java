package com.localcart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localcart.dto.ProductImportBatchRequest;
import com.localcart.dto.ProductImportBatchResponse;
import com.localcart.dto.ProductImportItem;
import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueReviewStatus;
import com.localcart.entity.ProductCategory;
import com.localcart.entity.ProductCatalogueSourceRecord;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.ProductCatalogueSourceRecordRepository;
import com.localcart.repository.ProductCategoryRepository;
import com.localcart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCatalogImportServiceTest {

    private static final String SOURCE_NAME = "Mendeley Indian Packaged Foods Dataset (2026)";
    private static final String SOURCE_URL = "https://data.mendeley.com/datasets/rhswn9z9xr/1";
    private static final String SOURCE_LICENSE = "CC-BY-4.0";
    private static final String DOI = "10.17632/rhswn9z9xr.1";

    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository categoryRepository;
    @Mock private ProductCatalogueSourceRecordRepository sourceRecordRepository;

    private ProductCatalogImportService service;
    private ProductCategory parent;
    private ProductCategory subcategory;
    private com.localcart.config.ApprovedCatalogueSourceProperties approvedSource;

    @BeforeEach
    void setUp() {
        approvedSource = new com.localcart.config.ApprovedCatalogueSourceProperties();
        service = new ProductCatalogImportService(
                productRepository, categoryRepository, sourceRecordRepository,
                new ProductCatalogueQualityService(), new ObjectMapper(), approvedSource);
        parent = new ProductCategory("SNACKS", "snacks", null);
        ReflectionTestUtils.setField(parent, "id", 1L);
        subcategory = new ProductCategory("CHIPS", "snacks-chips", parent);
        ReflectionTestUtils.setField(subcategory, "id", 2L);
    }

    @Test
    void importsMendeleyReferenceRowsAsReviewCandidatesWithoutRetailFacts() {
        mockCategory();
        when(productRepository.findByIdentityKey(anyString())).thenReturn(Optional.empty());
        when(sourceRecordRepository.findBySourceNameAndSourceRecordId(SOURCE_NAME, "mendeley-ipf-2026-v1:0001"))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 44L);
            return product;
        });

        ProductImportBatchResponse response = service.importBatch(batch(item("mendeley-ipf-2026-v1:0001")));

        assertEquals(1, response.received());
        assertEquals(1, response.inserted());
        assertEquals(0, response.rejected());
        verify(productRepository).save(argThat(product ->
                product.getId().equals(44L)
                        && product.getName().equals("Sample Chips")
                        && product.getBrand().equals("Sample Brand")
                        && product.getBarcode() == null
                        && product.getPackSize() == null
                        && product.getNetQuantity() == null
                        && product.getMrp() == null
                        && product.getCategory().equals("SNACKS")
                        && product.getSubcategory().equals("CHIPS")
                        && product.getCatalogueReviewStatus() == ProductCatalogueReviewStatus.NEEDS_REVIEW
                        && product.getCatalogueReviewReason().contains("verified retail GTIN and pack size")));
        verify(sourceRecordRepository).save(argThat(source ->
                source.getSourceName().equals(SOURCE_NAME)
                        && source.getSourceLicense().equals(SOURCE_LICENSE)
                        && source.getSourceRecordId().equals("mendeley-ipf-2026-v1:0001")
                        && source.getSourceMetadataJson().contains("\"Price_INR\":\"39.00\"")
                        && Boolean.TRUE.equals(source.getReuseRightsVerified())
                        && Boolean.TRUE.equals(source.getCommercialUsePermitted())
                        && source.getPermissionReference().equals(DOI)
                        && source.getProduct().getId().equals(44L)));
    }

    @Test
    void preservesSourceReviewFlagAndKeepsAmbiguousRowsOutOfCustomerVisibility() {
        mockCategory();
        when(productRepository.findByIdentityKey(anyString())).thenReturn(Optional.empty());
        when(sourceRecordRepository.findBySourceNameAndSourceRecordId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 45L);
            return product;
        });

        ProductImportItem needsReview = item("mendeley-ipf-2026-v1:0023",
                "Repeated product identity needs pack/variant verification");
        ProductImportBatchResponse response = service.importBatch(batch(needsReview));

        assertEquals(1, response.inserted());
        verify(productRepository).save(argThat(product ->
                product.getCatalogueReviewStatus() == ProductCatalogueReviewStatus.NEEDS_REVIEW
                        && product.getCatalogueReviewReason().contains("pack/variant verification")));
    }

    @Test
    void keepsRepeatedSourceRowsDistinctWhenTheirRetailPackIdentityIsUnknown() {
        mockCategory();
        when(productRepository.findByIdentityKey(anyString())).thenReturn(Optional.empty());
        when(sourceRecordRepository.findBySourceNameAndSourceRecordId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductImportItem first = item("mendeley-ipf-2026-v1:0023",
                "Repeated item name and brand in source");
        ProductImportItem second = item("mendeley-ipf-2026-v1:0060",
                "Repeated item name and brand in source");

        ProductImportBatchResponse response = service.importBatch(batch(List.of(first, second)));

        assertEquals(2, response.inserted());
        ArgumentCaptor<Product> products = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(2)).save(products.capture());
        assertNotEquals(products.getAllValues().get(0).getIdentityKey(),
                products.getAllValues().get(1).getIdentityKey());
        assertTrue(products.getAllValues().stream()
                .allMatch(product -> product.getCatalogueReviewStatus() == ProductCatalogueReviewStatus.NEEDS_REVIEW));
    }

    @Test
    void rejectsUnverifiedBarcodePackSizeQuantityAndPriceFields() {
        List<ProductImportItem> invalidItems = List.of(
                new ProductImportItem("Sample Chips", "Brand", "4006381333931", null,
                        null, null, null, null, "SNACKS", "CHIPS", null, null,
                        "mendeley-ipf-2026-v1:0001", null, metadata(), "mendeley-ipf-2026-v1:0001", null),
                new ProductImportItem("Sample Chips", "Brand", null, "250 g",
                        null, null, null, null, "SNACKS", "CHIPS", null, null,
                        "mendeley-ipf-2026-v1:0002", null, metadata(), "mendeley-ipf-2026-v1:0002", null),
                new ProductImportItem("Sample Chips", "Brand", null, null,
                        new BigDecimal("250"), "g", null, null, "SNACKS", "CHIPS", null, null,
                        "mendeley-ipf-2026-v1:0003", null, metadata(), "mendeley-ipf-2026-v1:0003", null),
                new ProductImportItem("Sample Chips", "Brand", null, null,
                        null, null, null, null, "SNACKS", "CHIPS", new BigDecimal("120.00"), "brand",
                        "mendeley-ipf-2026-v1:0004", null, metadata(), "mendeley-ipf-2026-v1:0004", null)
        );

        ProductImportBatchResponse response = service.importBatch(batch(invalidItems));

        assertEquals(4, response.rejected());
        assertTrue(response.errors().stream().allMatch(error -> error.contains("must not populate unverified")));
        verifyNoInteractions(categoryRepository, productRepository, sourceRecordRepository);
    }

    @Test
    void rejectsMalformedSourceMetadataJson() {
        ProductImportItem invalid = new ProductImportItem(
                "Sample Chips", "Sample Brand", null, null, null, null,
                null, null, "SNACKS", "CHIPS", null, null,
                "mendeley-ipf-2026-v1:0001", null, "{not-json}",
                "mendeley-ipf-2026-v1:0001", null);

        ProductImportBatchResponse response = service.importBatch(batch(invalid));

        assertEquals(1, response.rejected());
        assertTrue(response.errors().get(0).contains("sourceMetadataJson must contain valid JSON"));
        verifyNoInteractions(categoryRepository, productRepository, sourceRecordRepository);
    }

    @Test
    void rejectsCommercialImportWithoutBothExplicitRightsFlags() {
        ProductImportBatchRequest request = new ProductImportBatchRequest(
                SOURCE_NAME, SOURCE_URL, SOURCE_LICENSE, attribution(),
                false, true, DOI, List.of(item("mendeley-ipf-2026-v1:0001")));

        assertThrows(AccessDeniedException.class, () -> service.importBatch(request));
        verifyNoInteractions(productRepository, categoryRepository, sourceRecordRepository);
    }

    @Test
    void rejectsAttributionThatDoesNotRetainDatasetDoiAndLicense() {
        ProductImportBatchRequest request = new ProductImportBatchRequest(
                SOURCE_NAME, SOURCE_URL, SOURCE_LICENSE,
                "Dataset citation only; no licence reference.",
                true, true, DOI, List.of(item("mendeley-ipf-2026-v1:0001")));

        assertThrows(BadRequestException.class, () -> service.importBatch(request));
        verifyNoInteractions(productRepository, categoryRepository, sourceRecordRepository);
    }

    @Test
    void rejectsUnapprovedSourceNamesAndUrls() {
        ProductImportBatchRequest unapprovedSource = new ProductImportBatchRequest(
                "Unapproved packaged-food dataset",
                "https://example.org/catalogue.csv",
                "UNAPPROVED", attribution(), true, true, DOI,
                List.of(item("mendeley-ipf-2026-v1:0001")));
        ProductImportBatchRequest unapprovedUrl = new ProductImportBatchRequest(
                SOURCE_NAME, "https://example.com/dataset.csv", SOURCE_LICENSE,
                attribution(), true, true, DOI, List.of(item("mendeley-ipf-2026-v1:0001")));

        assertThrows(BadRequestException.class, () -> service.importBatch(unapprovedSource));
        assertThrows(BadRequestException.class, () -> service.importBatch(unapprovedUrl));
        verifyNoInteractions(productRepository, categoryRepository, sourceRecordRepository);
    }

    @Test
    void importsConfiguredApprovedRetailRowsAsReviewCandidates() {
        approvedSource.setName("Approved Retail Partner");
        approvedSource.setUrl("https://catalogue.partner.example/catalogue");
        approvedSource.setLicense("COMMERCIAL-PARTNER-LICENSE");
        approvedSource.setPermissionReference("CONTRACT-2026-APNIKO-01");
        approvedSource.setRightsVerified(true);
        approvedSource.setCommercialUsePermitted(true);

        mockCategory();
        when(productRepository.findByBarcode("8901058001235")).thenReturn(Optional.empty());
        when(sourceRecordRepository.findBySourceNameAndSourceRecordId(
                "Approved Retail Partner", "partner:sku-1001")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 91L);
            return product;
        });

        ProductImportItem retailItem = new ProductImportItem(
                "Parle-G Gold", "Parle", "8901058001235", "100 g",
                new BigDecimal("100"), "g", "parle-g", "Glucose biscuits",
                "SNACKS", "BISCUITS", new BigDecimal("20.00"), "pack",
                "partner:sku-1001", null,
                "{\\"sourceRecordId\\":\\"partner:sku-1001\\",\\"verifiedVariant\\":true}",
                "partner:sku-1001", null);
        ProductImportBatchResponse response = service.importBatch(approvedRetailBatch(retailItem));

        assertEquals(1, response.inserted());
        assertEquals(0, response.rejected());
        verify(productRepository).save(argThat(product ->
                product.getBarcode().equals("8901058001235")
                        && product.getPackSize().equals("100 g")
                        && product.getNetQuantity().compareTo(new BigDecimal("100")) == 0
                        && product.getCatalogueReviewStatus() == ProductCatalogueReviewStatus.NEEDS_REVIEW
                        && product.getCatalogueReviewReason().contains("pending administrator review")));
        verify(sourceRecordRepository).save(argThat(source ->
                source.getSourceName().equals("Approved Retail Partner")
                        && source.getPermissionReference().equals("CONTRACT-2026-APNIKO-01")
                        && Boolean.TRUE.equals(source.getReuseRightsVerified())
                        && Boolean.TRUE.equals(source.getCommercialUsePermitted())));
    }

    @Test
    void rejectsRetailSourceThatIsNotConfiguredAndApprovedByTheOperator() {
        ProductImportBatchRequest request = approvedRetailBatch(
                new ProductImportItem("Parle-G", "Parle", "8901058001235", "100 g",
                        new BigDecimal("100"), "g", null, null, "SNACKS", "BISCUITS",
                        null, null, "partner:sku-1", null, "{\\"source\\":\\"partner\\"}",
                        "partner:sku-1", null));

        assertThrows(BadRequestException.class, () -> service.importBatch(request));
        verifyNoInteractions(productRepository, categoryRepository, sourceRecordRepository);
    }

    @Test
    void rejectsApprovedRetailRowsWithoutVerifiedRetailIdentity() {
        approvedSource.setName("Approved Retail Partner");
        approvedSource.setUrl("https://catalogue.partner.example/catalogue");
        approvedSource.setLicense("COMMERCIAL-PARTNER-LICENSE");
        approvedSource.setPermissionReference("CONTRACT-2026-APNIKO-01");
        approvedSource.setRightsVerified(true);
        approvedSource.setCommercialUsePermitted(true);

        ProductImportItem incomplete = new ProductImportItem(
                "Parle-G Gold", "Parle", null, "100 g", null, null, null, null,
                "SNACKS", "BISCUITS", null, null, "partner:sku-2", null,
                "{\\"source\\":\\"partner\\"}", "partner:sku-2", null);
        ProductImportBatchResponse response = service.importBatch(approvedRetailBatch(incomplete));

        assertEquals(1, response.rejected());
        assertTrue(response.errors().get(0).contains("verified GTIN barcode"));
        verifyNoInteractions(productRepository, categoryRepository, sourceRecordRepository);
    }

    private ProductImportBatchRequest approvedRetailBatch(ProductImportItem item) {
        return new ProductImportBatchRequest(
                "Approved Retail Partner",
                "https://catalogue.partner.example/catalogue",
                "COMMERCIAL-PARTNER-LICENSE",
                "Approved Retail Partner; COMMERCIAL-PARTNER-LICENSE; CONTRACT-2026-APNIKO-01; Apniko modifications.",
                true, true, "CONTRACT-2026-APNIKO-01", List.of(item));
    }

    private void mockCategory() {
        when(categoryRepository.findByNameIgnoreCaseAndParentIsNull("SNACKS"))
                .thenReturn(Optional.of(parent));
        when(categoryRepository.findByNameIgnoreCaseAndParent_Id("CHIPS", 1L))
                .thenReturn(Optional.of(subcategory));
    }

    private ProductImportBatchRequest batch(ProductImportItem item) {
        return batch(List.of(item));
    }

    private ProductImportBatchRequest batch(List<ProductImportItem> items) {
        return new ProductImportBatchRequest(
                SOURCE_NAME, SOURCE_URL, SOURCE_LICENSE, attribution(),
                true, true, DOI, items);
    }

    private String attribution() {
        return "Duddela Sai Prashanth et al. (2026), Indian Packaged Foods Nutritional Composition Dataset (2026), "
                + "Version 1, Mendeley Data, DOI: " + DOI
                + ", CC BY 4.0. Apniko modifications: validation and normalization.";
    }

    private String metadata() {
        return "{\"dataset\":{\"doi\":\"" + DOI + "\"},"
                + "\"sourceValues\":{\"Price_INR\":\"39.00\",\"Serving_Size_g\":\"100\"}}";
    }

    private ProductImportItem item(String sourceRecordId) {
        return item(sourceRecordId, null);
    }

    private ProductImportItem item(String sourceRecordId, String sourceReviewReason) {
        return new ProductImportItem(
                "Sample Chips", "Sample Brand", null, null, null, null,
                null, null, "SNACKS", "CHIPS", null, null,
                sourceRecordId, null, metadata(), sourceRecordId, sourceReviewReason);
    }
}
