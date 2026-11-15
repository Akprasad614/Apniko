package com.localcart.service;

import com.localcart.dto.ProductImportBatchRequest;
import com.localcart.dto.ProductImportBatchResponse;
import com.localcart.dto.ProductImportItem;
import com.localcart.entity.Product;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
                productRepository, categoryRepository, sourceRecordRepository);
        parent = new ProductCategory("Beverages", "beverages", null);
        ReflectionTestUtils.setField(parent, "id", 1L);
        subcategory = new ProductCategory("Tea, Coffee & Cocoa",
                "beverages-tea-coffee-cocoa", parent);
        ReflectionTestUtils.setField(subcategory, "id", 2L);
    }

    @Test
    void importsNewProductAndKeepsSourceProvenance() {
        mockCategory();
        when(productRepository.findByBarcode("4006381333931")).thenReturn(Optional.empty());
        when(sourceRecordRepository.findBySourceNameAndSourceRecordId(
                "PackSense India / Open Food Facts", "4006381333931"))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 44L);
            return product;
        });

        ProductImportBatchResponse response = service.importBatch(batch(item(
                "4006381333931", "Sample Tea", "Sample Brand", "250 g", null, null)));

        assertEquals(1, response.received());
        assertEquals(1, response.inserted());
        assertEquals(0, response.rejected());
        verify(productRepository).save(argThat(product ->
                product.getId().equals(44L)
                        && product.getName().equals("Sample Tea")
                        && product.getBrand().equals("Sample Brand")
                        && product.getPackSize().equals("250 g")
                        && product.getCategory().equals("Beverages")
                        && product.getSubcategory().equals("Tea, Coffee & Cocoa")));
        verify(sourceRecordRepository).save(argThat(source ->
                source.getSourceName().equals("PackSense India / Open Food Facts")
                        && source.getSourceLicense().equals("ODbL-1.0")
                        && source.getSourceRecordId().equals("4006381333931")
                        && source.getProduct().getId().equals(44L)));
    }

    @Test
    void rejectsInvalidBarcodeWithoutWritingCatalogueRows() {
        ProductImportBatchResponse response = service.importBatch(
                batch(item("4006381333932", "Bad Barcode", "Brand", "250 g", null, null)));

        assertEquals(1, response.rejected());
        assertTrue(response.errors().get(0).contains("invalid GTIN check digit"));
        verifyNoInteractions(categoryRepository);
        verify(productRepository, never()).save(any());
        verify(sourceRecordRepository, never()).save(any());
    }

    @Test
    void rejectsMrpWithoutAnApprovedSource() {
        ProductImportItem invalid = item(
                "4006381333931", "Sample Tea", "Sample Brand", "250 g",
                new BigDecimal("120.00"), "retailer");
        ProductImportBatchResponse response = service.importBatch(batch(invalid));

        assertEquals(1, response.rejected());
        assertTrue(response.errors().get(0).contains("MRP source must be pack, brand, gov or off"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsSourceWithoutSecureUrlOrApprovedLicence() {
        ProductImportBatchRequest insecure = new ProductImportBatchRequest(
                "Test", "http://example.com/catalogue.csv", "ODbL-1.0",
                "Test attribution", java.util.List.of(item(
                        "4006381333931", "Sample Tea", "Brand", "250 g", null, null)));
        assertThrows(BadRequestException.class, () -> service.importBatch(insecure));

        ProductImportBatchRequest unknownLicence = new ProductImportBatchRequest(
                "Test", "https://example.com/catalogue.csv", "UNKNOWN",
                "Test attribution", java.util.List.of(item(
                        "4006381333931", "Sample Tea", "Brand", "250 g", null, null)));
        assertThrows(BadRequestException.class, () -> service.importBatch(unknownLicence));
    }

    @Test
    void rejectsUnknownCategoryWithoutCreatingProduct() {
        when(categoryRepository.findByNameIgnoreCaseAndParentIsNull("Unknown"))
                .thenReturn(Optional.empty());

        ProductImportItem invalid = new ProductImportItem(
                "Sample", "Brand", "4006381333931", "250 g",
                new BigDecimal("250"), "g", null, null,
                "Unknown", "Some Subcategory", null, null,
                "4006381333931", null);

        ProductImportBatchResponse response = service.importBatch(batch(invalid));
        assertEquals(1, response.rejected());
        assertTrue(response.errors().get(0).contains("unknown category"));
        verify(productRepository, never()).save(any());
    }

    private void mockCategory() {
        when(categoryRepository.findByNameIgnoreCaseAndParentIsNull("Beverages"))
                .thenReturn(Optional.of(parent));
        when(categoryRepository.findByNameIgnoreCaseAndParent_Id(
                "Tea, Coffee & Cocoa", 1L)).thenReturn(Optional.of(subcategory));
    }

    private ProductImportBatchRequest batch(ProductImportItem item) {
        return new ProductImportBatchRequest(
                "PackSense India / Open Food Facts",
                "https://huggingface.co/datasets/PackSense/packsense-india",
                "ODbL-1.0",
                "PackSense India, derived from Open Food Facts; ODbL-1.0.",
                java.util.List.of(item));
    }

    private ProductImportItem item(String barcode, String name, String brand,
                                   String packSize, BigDecimal mrp, String mrpSource) {
        return new ProductImportItem(
                name, brand, barcode, packSize, new BigDecimal("250"), "g",
                null, null, "Beverages", "Tea, Coffee & Cocoa",
                mrp, mrpSource, barcode, null);
    }
}
