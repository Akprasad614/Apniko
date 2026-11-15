package com.localcart.service;

import com.localcart.dto.ProductPhotoCandidateRequest;
import com.localcart.dto.ProductPhotoReviewRequest;
import com.localcart.entity.Product;
import com.localcart.entity.ProductImageReviewStatus;
import com.localcart.entity.ProductPhoto;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.ProductPhotoRepository;
import com.localcart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductPhotoServiceTest {
    private static final String BARCODE = "8901234567890";
    private static final String IMAGE = "https://images.openfoodfacts.org/images/products/890/123/456/7890/front_en.4.400.jpg";
    private static final String SOURCE = "https://world.openfoodfacts.org/product/" + BARCODE;
    private ProductRepository productRepository;
    private ProductPhotoRepository photoRepository;
    private ProductPhotoService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        photoRepository = mock(ProductPhotoRepository.class);
        service = new ProductPhotoService(productRepository, photoRepository);
    }

    @Test
    void candidateIsPendingAndNotCustomerVisibleBeforeApproval() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.submitCandidate(10L, candidate(IMAGE, BARCODE));

        assertEquals(ProductImageReviewStatus.PENDING, result.status());
        assertNull(result.approvedImageUrl());
        assertEquals(IMAGE, result.candidateImageUrl());
        verify(photoRepository).save(any(ProductPhoto.class));
    }

    @Test
    void candidateRequiresExactBarcodeAndOpenFactsImageHost() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class,
                () -> service.submitCandidate(10L, candidate(IMAGE, "8901234567891")));
        assertThrows(BadRequestException.class,
                () -> service.submitCandidate(10L, candidate(
                        "https://example.com/images/products/8901234567890/front.jpg", BARCODE)));
        verify(photoRepository, never()).save(any(ProductPhoto.class));
    }

    @Test
    void approvalPublishesPhotoWithSourceLicenceAndAttribution() {
        Product product = product();
        ProductPhoto photo = pendingPhoto(product);
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.of(photo));
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.reviewCandidate(
                10L, new ProductPhotoReviewRequest(ProductImageReviewStatus.APPROVED, null),
                "admin@example.test");

        assertEquals(ProductImageReviewStatus.APPROVED, result.status());
        assertEquals(IMAGE, result.approvedImageUrl());
        assertEquals("Open Food Facts", photo.getSourceName());
        assertEquals("CC BY-SA", photo.getLicense());
        assertEquals("Open Food Facts contributors", photo.getAttribution());
        assertNull(photo.getCandidateImageUrl());
    }

    @Test
    void rejectingReplacementKeepsPreviousApprovedImage() {
        Product product = product();
        ProductPhoto photo = pendingPhoto(product);
        photo.setApprovedImageUrl("https://images.openfoodfacts.org/images/products/890/123/456/7890/old.jpg");
        photo.setSourceName("Open Food Facts");
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.of(photo));
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.reviewCandidate(
                10L, new ProductPhotoReviewRequest(ProductImageReviewStatus.REJECTED, "Different packaging"),
                "admin@example.test");

        assertEquals(ProductImageReviewStatus.APPROVED, result.status());
        assertTrue(result.approvedImageUrl().endsWith("/old.jpg"));
        assertNull(photo.getCandidateImageUrl());
    }

    @Test
    void rejectionRequiresReason() {
        ProductPhoto photo = pendingPhoto(product());
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.of(photo));

        assertThrows(BadRequestException.class, () -> service.reviewCandidate(
                10L, new ProductPhotoReviewRequest(ProductImageReviewStatus.REJECTED, " "),
                "admin@example.test"));
        verify(photoRepository, never()).save(any(ProductPhoto.class));
    }

    private Product product() {
        Product product = new Product();
        product.setName("Test grocery");
        product.setBarcode(BARCODE);
        return product;
    }

    private ProductPhoto pendingPhoto(Product product) {
        ProductPhoto photo = new ProductPhoto();
        photo.setProduct(product);
        photo.setCandidateImageUrl(IMAGE);
        photo.setCandidateSourceName("Open Food Facts");
        photo.setCandidateSourceUrl(SOURCE);
        photo.setCandidateLicense("CC BY-SA");
        photo.setCandidateAttribution("Open Food Facts contributors");
        photo.setCandidateProductName("Test grocery pack");
        photo.setCandidateBarcode(BARCODE);
        photo.setReviewStatus(ProductImageReviewStatus.PENDING);
        return photo;
    }

    private ProductPhotoCandidateRequest candidate(String image, String barcode) {
        return new ProductPhotoCandidateRequest(
                image, SOURCE, "Open Food Facts", "Test grocery pack",
                "CC BY-SA", "Open Food Facts contributors", barcode);
    }
}
