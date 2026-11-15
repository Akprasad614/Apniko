package com.localcart.service;

import com.localcart.dto.CatalogueReviewUpdateRequest;
import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueReviewStatus;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductCatalogueModerationServiceTest {

    private ProductRepository productRepository;
    private ProductCatalogueModerationService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        service = new ProductCatalogueModerationService(productRepository);
    }

    @Test
    void defaultsReviewQueueToNeedsReview() {
        Product product = new Product();
        product.setName("Unclassified item");
        product.setCatalogueReviewStatus(ProductCatalogueReviewStatus.NEEDS_REVIEW);
        ReflectionTestUtils.setField(product, "id", 25L);
        when(productRepository.searchCatalogueReviewQueue(
                eq(ProductCatalogueReviewStatus.NEEDS_REVIEW), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 50), 1));

        var result = service.getReviewQueue(null, 0, 50);

        assertEquals(1, result.getTotalElements());
        assertEquals(25L, result.getContent().get(0).id());
        assertEquals(ProductCatalogueReviewStatus.NEEDS_REVIEW,
                result.getContent().get(0).status());
    }

    @Test
    void searchFiltersProductsAcrossNameBrandBarcodeAndCategory() {
        Product product = new Product();
        product.setName("Poha");
        product.setCatalogueReviewStatus(ProductCatalogueReviewStatus.NEEDS_REVIEW);
        ReflectionTestUtils.setField(product, "id", 26L);
        when(productRepository.searchCatalogueReviewQueue(
                eq(ProductCatalogueReviewStatus.NEEDS_REVIEW), eq("poha"), any()))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1));

        var result = service.getReviewQueue(" needs_review ", " poha ", 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals("Poha", result.getContent().get(0).name());
        verify(productRepository).searchCatalogueReviewQueue(
                eq(ProductCatalogueReviewStatus.NEEDS_REVIEW), eq("poha"), any());
    }

    @Test
    void allStatusIncludesLegacyProductsAndRejectsLongSearchTerms() {
        when(productRepository.searchCatalogueReviewQueue(
                isNull(), eq("rice"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = service.getReviewQueue("ALL", "rice", 0, 20);

        assertTrue(result.isEmpty());
        verify(productRepository).searchCatalogueReviewQueue(isNull(), eq("rice"), any());
        assertThrows(BadRequestException.class,
                () -> service.getReviewQueue("ALL", "x".repeat(101), 0, 20));
    }

    @Test
    void approvalStoresWhoReviewedTheProductAndMakesItVisible() {
        Product product = new Product();
        product.setName("Suhana Schezwan Chutney");
        product.setCatalogueReviewStatus(ProductCatalogueReviewStatus.NEEDS_REVIEW);
        product.setCatalogueReviewReason("Product category is unclassified");
        ReflectionTestUtils.setField(product, "id", 31L);
        when(productRepository.findById(31L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateReview(
                31L,
                new CatalogueReviewUpdateRequest(ProductCatalogueReviewStatus.ACTIVE, null),
                "admin@example.test");

        assertEquals(ProductCatalogueReviewStatus.ACTIVE, response.status());
        assertNull(response.reviewReason());
        assertEquals("admin@example.test", response.reviewedBy());
        assertNotNull(response.reviewedAt());
        verify(productRepository).save(product);
    }

    @Test
    void hidingRequiresAReason() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 32L);
        when(productRepository.findById(32L)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class, () -> service.updateReview(
                32L,
                new CatalogueReviewUpdateRequest(ProductCatalogueReviewStatus.HIDDEN, " "),
                "admin@example.test"));

        verify(productRepository, never()).save(any());
    }

    @Test
    void requeueRecordsTheReviewerAndProtectsTheDecisionFromFutureImports() {
        Product product = new Product();
        product.setName("Unclassified item");
        product.setCatalogueReviewStatus(ProductCatalogueReviewStatus.ACTIVE);
        product.setCatalogueReviewedBy("admin@example.test");
        product.setCatalogueReviewedAt(java.time.Instant.now());
        ReflectionTestUtils.setField(product, "id", 33L);
        when(productRepository.findById(33L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateReview(
                33L,
                new CatalogueReviewUpdateRequest(
                        ProductCatalogueReviewStatus.NEEDS_REVIEW, "Please verify packaging"),
                "admin@example.test");

        assertEquals(ProductCatalogueReviewStatus.NEEDS_REVIEW, response.status());
        assertNotNull(response.reviewedAt());
        assertEquals("admin@example.test", response.reviewedBy());
        assertEquals("Please verify packaging", response.reviewReason());
    }
}
