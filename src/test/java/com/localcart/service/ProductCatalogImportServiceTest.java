package com.localcart.service;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository categoryRepository;
    @Mock private ProductCatalogueSourceRecordRepository sourceRecordRepository;

    private ProductCatalogImportService service;
    private ProductCategory parent;
    private ProductCategory subcategory;

    @BeforeEach
    void setUp() {
        service = new ProductCatalogImportService(
                productRepository, categoryRepository, sourceRecordRepository,
                new ProductCatalogueQualityService());
        parent = new ProductCategory("Beverages", "beverages", null);
        ReflectionTestUtils.setField(parent, "id", 1L);
        subcategory = new ProductCategory("Tea, Coffee & Cocoa",
                "beverages-tea-coffee-cocoa", parent);
        ReflectionTestUtils.setField(subcategory, "id", 2L);
    }

    @Test
    void importsOnlyBigBasketIdentityFieldsAndPersistsPermissionEvidence() {
        mockCategory();
        when(productRepository.findByIdentityKey(anyString())).thenReturn(Optional.empty());
        when(sourceRecordRepository.findBySourceNameAndSourceRecordId(
                "BigBasket authorised dataset", "bigbasket:sample-tea-1"))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 44L);
            return product;
        });

        ProductImportBatchResponse response = service.importBatch(batch(item("bigbasket:sample-tea-1")));

        assertEquals(1, response.received());
        assertEquals(1, response.inserted());
        assertEquals(0, response.rejected());
        verify(productRepository).save(argThat(product ->
                product.getId().equals(44L)
                        && product.getName().equals("Sample Tea")
                        && product.getBrand().equals("Sample Brand")
                        && product.getBarcode() == null
                        && product.getPackSize() == null
                        && product.getMrp() == null
                        && product.getCategory().equals("Beverages")
                        && product.getSubcategory().equals("Tea, Coffee & Cocoa")
                        && product.getCatalogueReviewStatus() == ProductCatalogueReviewStatus.ACTIVE));
        verify(sourceRecordRepository).save(argThat(source ->
                source.getSourceName().equals("BigBasket authorised dataset")
                        && source.getSourceLicense().equals("BIGBASKET-WRITTEN-PERMISSION")
                        && source.getSourceRecordId().equals("bigbasket:sample-tea-1")
                        && Boolean.TRUE.equals(source.getReuseRightsVerified())
                        && Boolean.TRUE.equals(source.getCommercialUsePermitted())
                        && source.getPermissionReference().equals("TEST-BIGBASKET-GRANT")
                        && source.getProduct().getId().equals(44L)));
    }

    @Test
    void rejectsUnverifiedBarcodePackSizeQuantityAndPriceFields() {
        List<ProductImportItem> invalidItems = List.of(
                new ProductImportItem("Sample Tea", "Brand", "4006381333931", null,
                        null, null, null, null, "Beverages", "Tea", null, null, "bigbasket:barcode", null),
                new ProductImportItem("Sample Tea", "Brand", null, "250 g",
                        null, null, null, null, "Beverages", "Tea", null, null, "bigbasket:pack", null),
                new ProductImportItem("Sample Tea", "Brand", null, null,
                        new BigDecimal("250"), "g", null, null, "Beverages", "Tea", null, null, "bigbasket:qty", null),
                new ProductImportItem("Sample Tea", "Brand", null, null,
                        null, null, null, null, "Beverages", "Tea", new BigDecimal("120.00"), "brand", "bigbasket:mrp", null)
        );

        ProductImportBatchResponse response = service.importBatch(batch(invalidItems));

        assertEquals(4, response.rejected());
        assertTrue(response.errors().stream().allMatch(error -> error.contains("must not include unverified barcode")));
        verifyNoInteractions(categoryRepository, productRepository, sourceRecordRepository);
    }

    @Test
    void rejectsCommercialImportWithoutBothExplicitPermissionFlags() {
        ProductImportBatchRequest request = new ProductImportBatchRequest(
                "BigBasket authorised dataset",
                "https://www.kaggle.com/datasets/surajjha101/bigbasket-entire-product-list-28k-datapoints",
                "BIGBASKET-WRITTEN-PERMISSION",
                attribution("TEST-BIGBASKET-GRANT"),
                false, true, "TEST-BIGBASKET-GRANT", List.of(item("bigbasket:tea-1")));

        assertThrows(BadRequestException.class, () -> service.importBatch(request));
        verifyNoInteractions(productRepository, categoryRepository, sourceRecordRepository);
    }

    @Test
    void rejectsAttributionThatDoesNotMatchPermissionReference() {
        ProductImportBatchRequest request = new ProductImportBatchRequest(
                "BigBasket authorised dataset",
                "https://www.kaggle.com/datasets/surajjha101/bigbasket-entire-product-list-28k-datapoints",
                "BIGBASKET-WRITTEN-PERMISSION",
                "Commercial use: authorised. Permission reference: DIFFERENT-REFERENCE.",
                true, true, "TEST-BIGBASKET-GRANT", List.of(item("bigbasket:tea-1")));

        assertThrows(BadRequestException.class, () -> service.importBatch(request));
        verifyNoInteractions(productRepository, categoryRepository, sourceRecordRepository);
    }

    @Test
    void rejectsUnapprovedSourceUrlAndSourceIdentity() {
        ProductImportBatchRequest insecure = new ProductImportBatchRequest(
                "BigBasket authorised dataset", "http://example.com/catalogue.csv",
                "BIGBASKET-WRITTEN-PERMISSION", attribution("TEST-BIGBASKET-GRANT"),
                true, true, "TEST-BIGBASKET-GRANT", List.of(item("bigbasket:tea-1")));
        ProductImportBatchRequest arbitraryKagglePage = new ProductImportBatchRequest(
                "BigBasket authorised dataset", "https://www.kaggle.com/datasets/other/dataset",
                "BIGBASKET-WRITTEN-PERMISSION", attribution("TEST-BIGBASKET-GRANT"),
                true, true, "TEST-BIGBASKET-GRANT", List.of(item("bigbasket:tea-1")));

        assertThrows(BadRequestException.class, () -> service.importBatch(insecure));
        assertThrows(BadRequestException.class, () -> service.importBatch(arbitraryKagglePage));
        verifyNoInteractions(productRepository, categoryRepository, sourceRecordRepository);
    }

    @Test
    void createsCategoriesFromBigBasketTaxonomyWhenStartingFromEmptyCatalogue() {
        when(categoryRepository.findByNameIgnoreCaseAndParentIsNull("BigBasket Fresh"))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> {
            ProductCategory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", saved.getParent() == null ? 10L : 11L);
            return saved;
        });
        when(categoryRepository.findByNameIgnoreCaseAndParent_Id("Vegetables", 10L))
                .thenReturn(Optional.empty());
        when(productRepository.findByIdentityKey(anyString())).thenReturn(Optional.empty());
        when(sourceRecordRepository.findBySourceNameAndSourceRecordId(
                "BigBasket authorised dataset", "bigbasket:bhendi"))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 45L);
            return product;
        });

        ProductImportItem fresh = new ProductImportItem(
                "Bhendi", "Fresho", null, null, null, null,
                "Regular", "Fresh okra", "BigBasket Fresh", "Vegetables",
                null, null, "bigbasket:bhendi", null);

        ProductImportBatchResponse response = service.importBatch(batch(List.of(fresh)));

        assertEquals(1, response.inserted());
        verify(categoryRepository, times(2)).save(any(ProductCategory.class));
        verify(productRepository).save(any(Product.class));
    }

    private void mockCategory() {
        when(categoryRepository.findByNameIgnoreCaseAndParentIsNull("Beverages"))
                .thenReturn(Optional.of(parent));
        when(categoryRepository.findByNameIgnoreCaseAndParent_Id(
                "Tea, Coffee & Cocoa", 1L)).thenReturn(Optional.of(subcategory));
    }

    private ProductImportBatchRequest batch(ProductImportItem item) {
        return batch(List.of(item));
    }

    private ProductImportBatchRequest batch(List<ProductImportItem> items) {
        return new ProductImportBatchRequest(
                "BigBasket authorised dataset",
                "https://www.kaggle.com/datasets/surajjha101/bigbasket-entire-product-list-28k-datapoints",
                "BIGBASKET-WRITTEN-PERMISSION",
                attribution("TEST-BIGBASKET-GRANT"),
                true, true, "TEST-BIGBASKET-GRANT", items);
    }

    private String attribution(String reference) {
        return "BigBasket CSV fields only. Commercial use: authorised by written permission. Permission reference: "
                + reference + ".";
    }

    private ProductImportItem item(String sourceRecordId) {
        return new ProductImportItem(
                "Sample Tea", "Sample Brand", null, null, null, null,
                null, "Black tea", "Beverages", "Tea, Coffee & Cocoa",
                null, null, sourceRecordId, null);
    }
}
