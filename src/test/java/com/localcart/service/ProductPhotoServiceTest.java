package com.localcart.service;

import com.localcart.dto.ProductPhotoCandidateRequest;
import com.localcart.dto.ProductPhotoReviewRequest;
import com.localcart.entity.Product;
import com.localcart.entity.ProductImageReviewStatus;
import com.localcart.entity.ProductPhoto;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ConflictException;
import com.localcart.repository.ProductPhotoRepository;
import com.localcart.repository.ProductPhotoReviewHistoryRepository;
import com.localcart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductPhotoServiceTest {
    private static final String BARCODE = "8901234567890";
    private static final String IMAGE = "https://images.openfoodfacts.org/images/products/890/123/456/7890/front_en.4.400.jpg";
    private static final String SOURCE = "https://world.openfoodfacts.org/product/" + BARCODE;
    private static final String BRAND_SOURCE = "Brand / supplier authorised catalogue";
    private ProductRepository productRepository;
    private ProductPhotoRepository photoRepository;
    private ProductPhotoReviewHistoryRepository historyRepository;
    private ProductPhotoService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        photoRepository = mock(ProductPhotoRepository.class);
        historyRepository = mock(ProductPhotoReviewHistoryRepository.class);
        service = new ProductPhotoService(productRepository, photoRepository, historyRepository);
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
        assertEquals("Test Brand", result.sourceProductBrand());
        assertEquals("1 kg", result.sourcePackSize());
        assertNull(result.authorizationReference());
        verify(photoRepository).save(any(ProductPhoto.class));
    }

    @Test
    void previouslyRetiredImageUrlCannotBeSubmittedAgain() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(historyRepository.existsByProductIdAndCandidateImageUrlAndOutcomeIn(
                10L, IMAGE, List.of("REJECTED", "RETIRED"))).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.submitCandidate(10L, candidate(IMAGE, BARCODE)));
        verify(photoRepository, never()).save(any(ProductPhoto.class));
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
    void openFactsImageRequiresExactBrandAndPackSize() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        ProductPhotoCandidateRequest wrongBrand = new ProductPhotoCandidateRequest(
                IMAGE, SOURCE, "Open Food Facts", "Test grocery pack",
                "Different Brand", "1 kg", "CC BY-SA",
                "Open Food Facts contributors", null, BARCODE);
        ProductPhotoCandidateRequest wrongPack = new ProductPhotoCandidateRequest(
                IMAGE, SOURCE, "Open Food Facts", "Test grocery pack",
                "Test Brand", "500 g", "CC BY-SA",
                "Open Food Facts contributors", null, BARCODE);
        ProductPhotoCandidateRequest ambiguousPromoPack = new ProductPhotoCandidateRequest(
                IMAGE, SOURCE, "Open Food Facts", "Test grocery pack",
                "Test Brand", "5+1 kg", "CC BY-SA",
                "Open Food Facts contributors", null, BARCODE);

        assertThrows(BadRequestException.class,
                () -> service.submitCandidate(10L, wrongBrand));
        assertThrows(BadRequestException.class,
                () -> service.submitCandidate(10L, wrongPack));
        assertThrows(BadRequestException.class,
                () -> service.submitCandidate(10L, ambiguousPromoPack));
        verify(photoRepository, never()).save(any(ProductPhoto.class));
    }

    @Test
    void openFactsCandidateAcceptsEquivalentBrandAndPackSizeFormatting() {
        Product product = product();
        product.setBrand("Nestle");
        product.setPackSize("1 g");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        ProductPhotoCandidateRequest equivalent = new ProductPhotoCandidateRequest(
                IMAGE, SOURCE, "Open Food Facts", "Test grocery pack",
                "Nestlé", "1000 mg", "CC BY-SA",
                "Open Food Facts contributors", null, BARCODE);

        var result = service.submitCandidate(10L, equivalent);

        assertEquals(ProductImageReviewStatus.PENDING, result.status());
        assertEquals("Nestlé", result.sourceProductBrand());
        assertEquals("1000 mg", result.sourcePackSize());
        verify(photoRepository).save(any(ProductPhoto.class));
    }

    @Test
    void openFactsCandidateAcceptsEquivalentUsFluidOunceAndMetricPackSize() {
        Product product = product();
        product.setPackSize("473.176 ml");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        ProductPhotoCandidateRequest equivalent = new ProductPhotoCandidateRequest(
                IMAGE, SOURCE, "Open Food Facts", "Apple Cider Vinegar",
                "Test Brand", "16 FL OZ (473 ml)", "CC BY-SA",
                "Open Food Facts contributors", null, BARCODE);

        var result = service.submitCandidate(10L, equivalent);

        assertEquals(ProductImageReviewStatus.PENDING, result.status());
        assertEquals("16 FL OZ (473 ml)", result.sourcePackSize());
        verify(photoRepository).save(any(ProductPhoto.class));
    }

    @Test
    void openFactsCandidateAcceptsZeroPaddedUpcImageDirectory() {
        String upc = "036000291452";
        String paddedImage = "https://images.openfoodfacts.org/images/products/003/600/029/1452/front_en.2.400.jpg";
        String upcSource = "https://world.openfoodfacts.org/product/" + upc;
        Product product = product();
        product.setBarcode(upc);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        ProductPhotoCandidateRequest request = new ProductPhotoCandidateRequest(
                paddedImage, upcSource, "Open Food Facts", "Test grocery pack",
                "Test Brand", "1 kg", "CC BY-SA",
                "Open Food Facts contributors", null, upc);

        var result = service.submitCandidate(10L, request);

        assertEquals(ProductImageReviewStatus.PENDING, result.status());
        assertEquals(paddedImage, result.candidateImageUrl());
        verify(photoRepository).save(any(ProductPhoto.class));
    }

    @Test
    void openFactsCandidateStillRejectsMultipackTotalThatDiffersFromCatalogue() {
        Product product = product();
        product.setPackSize("100 g");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        ProductPhotoCandidateRequest wrongPack = new ProductPhotoCandidateRequest(
                IMAGE, SOURCE, "Open Food Facts", "Test grocery pack",
                "Test Brand", "2 x 100gm", "CC BY-SA",
                "Open Food Facts contributors", null, BARCODE);

        assertThrows(BadRequestException.class,
                () -> service.submitCandidate(10L, wrongPack));
        verify(photoRepository, never()).save(any(ProductPhoto.class));
    }

    @Test
    void manufacturerAuthorisedImageRequiresPermissionReferenceAndTrustedHost() {
        Product product = product();
        product.setBrand("Brand Example");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        ProductPhotoCandidateRequest authorised = new ProductPhotoCandidateRequest(
                "https://images.brand.example/products/masoor-1kg/front.jpg",
                "https://brand.example/products/masoor-dal",
                BRAND_SOURCE,
                "Masoor Dal",
                "Brand Example",
                "1 kg",
                "MANUFACTURER_AUTHORIZED",
                "Image supplied by the brand",
                "Supplier agreement BR-2026-014",
                BARCODE
        );

        var result = service.submitCandidate(10L, authorised);

        assertEquals(ProductImageReviewStatus.PENDING, result.status());
        assertEquals(BRAND_SOURCE, result.candidateSourceName());
        assertEquals("MANUFACTURER_AUTHORIZED", result.candidateLicense());
        assertEquals("Brand Example", result.sourceProductBrand());
        assertEquals("1 kg", result.sourcePackSize());
        assertEquals("Supplier agreement BR-2026-014", result.candidateAuthorizationReference());
    }

    @Test
    void manufacturerAuthorisedImageRejectsMissingPermissionOrUnapprovedImageHost() {
        Product product = product();
        product.setBrand("Brand Example");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        ProductPhotoCandidateRequest missingPermission = new ProductPhotoCandidateRequest(
                "https://images.brand.example/products/masoor-1kg/front.jpg",
                "https://brand.example/products/masoor-dal",
                BRAND_SOURCE, "Masoor Dal", "Brand Example", "1 kg",
                "MANUFACTURER_AUTHORIZED", "Brand image", null, BARCODE
        );
        ProductPhotoCandidateRequest unapprovedHost = new ProductPhotoCandidateRequest(
                "https://other-cdn.example/products/masoor-1kg/front.jpg",
                "https://brand.example/products/masoor-dal",
                BRAND_SOURCE, "Masoor Dal", "Brand Example", "1 kg",
                "MANUFACTURER_AUTHORIZED", "Brand image",
                "Supplier agreement BR-2026-014", BARCODE
        );

        assertThrows(BadRequestException.class,
                () -> service.submitCandidate(10L, missingPermission));
        assertThrows(BadRequestException.class,
                () -> service.submitCandidate(10L, unapprovedHost));
        verify(photoRepository, never()).save(any(ProductPhoto.class));
    }

    @Test
    void approvalPublishesPhotoWithSourceLicenceAndAttribution() {
        Product product = product();
        ProductPhoto photo = pendingPhoto(product);
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.of(photo));
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.reviewCandidate(
                10L, new ProductPhotoReviewRequest(ProductImageReviewStatus.APPROVED, null, true),
                "admin@example.test");

        assertEquals(ProductImageReviewStatus.APPROVED, result.status());
        assertEquals(IMAGE, result.approvedImageUrl());
        assertEquals("Open Food Facts", photo.getSourceName());
        assertEquals("CC BY-SA", photo.getLicense());
        assertEquals("Open Food Facts contributors", photo.getAttribution());
        assertNull(result.authorizationReference());
        assertNull(photo.getCandidateImageUrl());
    }

    @Test
    void approvalRequiresExplicitIdentityAndRightsConfirmation() {
        ProductPhoto photo = pendingPhoto(product());
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.of(photo));

        assertThrows(BadRequestException.class, () -> service.reviewCandidate(
                10L, new ProductPhotoReviewRequest(ProductImageReviewStatus.APPROVED, null, false),
                "admin@example.test"));
        verify(photoRepository, never()).save(any(ProductPhoto.class));
        assertNull(photo.getApprovedImageUrl());
    }

    @Test
    void approvalRetainsManufacturerPermissionReferenceForAudit() {
        Product product = product();
        ProductPhoto photo = pendingPhoto(product);
        photo.setCandidateSourceName(BRAND_SOURCE);
        photo.setCandidateSourceUrl("https://brand.example/products/masoor-dal");
        photo.setCandidateLicense("MANUFACTURER_AUTHORIZED");
        photo.setCandidateAuthorizationReference("Supplier agreement BR-2026-014");
        when(photoRepository.findByProductId(10L)).thenReturn(Optional.of(photo));
        when(photoRepository.save(any(ProductPhoto.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.reviewCandidate(
                10L, new ProductPhotoReviewRequest(ProductImageReviewStatus.APPROVED, null, true),
                "admin@example.test");

        assertEquals("Supplier agreement BR-2026-014", photo.getAuthorizationReference());
        assertEquals("MANUFACTURER_AUTHORIZED", photo.getLicense());
        assertEquals("Supplier agreement BR-2026-014", result.authorizationReference());
        assertNull(photo.getCandidateAuthorizationReference());
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
                10L, new ProductPhotoReviewRequest(ProductImageReviewStatus.REJECTED, "Different packaging", false),
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
                10L, new ProductPhotoReviewRequest(ProductImageReviewStatus.REJECTED, " ", false),
                "admin@example.test"));
        verify(photoRepository, never()).save(any(ProductPhoto.class));
    }


    @Test
    void retirementArchivesPendingCandidateAndMakesProductEligibleForFreshLookup() {
        ProductPhoto photo = pendingPhoto(product());
        when(photoRepository.findByReviewStatus(eq(ProductImageReviewStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(photo)), Page.empty());
        when(photoRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.retirePendingCandidates(
                "Legacy candidate retired before Phase 3B refresh",
                "admin@example.test",
                true);

        assertEquals(1, result.retiredCandidates());
        assertEquals(0, result.preservedPreviouslyApprovedPhotos());
        assertEquals(ProductImageReviewStatus.REJECTED, photo.getReviewStatus());
        assertNull(photo.getCandidateImageUrl());
        assertEquals("Legacy candidate retired before Phase 3B refresh", photo.getReviewReason());
        verify(historyRepository).save(any());
        verify(photoRepository, times(2)).findByReviewStatus(
                eq(ProductImageReviewStatus.PENDING), any(Pageable.class));
        verify(photoRepository).saveAll(any());
    }

    @Test
    void retirementPreservesPreviouslyApprovedPhotoAndRequiresConfirmation() {
        ProductPhoto photo = pendingPhoto(product());
        String approved = "https://images.openfoodfacts.org/images/products/890/123/456/7890/approved.jpg";
        photo.setApprovedImageUrl(approved);
        when(photoRepository.findByReviewStatus(eq(ProductImageReviewStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(photo)), Page.empty());
        when(photoRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        assertThrows(BadRequestException.class,
                () -> service.retirePendingCandidates("Retire pending candidates", "admin@example.test", false));
        verify(photoRepository, never()).saveAll(any());

        var result = service.retirePendingCandidates(
                "Retire old candidate, preserve approved image", "admin@example.test", true);

        assertEquals(1, result.retiredCandidates());
        assertEquals(1, result.preservedPreviouslyApprovedPhotos());
        assertEquals(ProductImageReviewStatus.APPROVED, photo.getReviewStatus());
        assertEquals(approved, photo.getApprovedImageUrl());
        verify(historyRepository).save(any());
    }

    private Product product() {
        Product product = new Product();
        product.setName("Test grocery");
        product.setBrand("Test Brand");
        product.setPackSize("1 kg");
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
        photo.setCandidateProductBrand("Test Brand");
        photo.setCandidatePackSize("1 kg");
        photo.setCandidateBarcode(BARCODE);
        photo.setReviewStatus(ProductImageReviewStatus.PENDING);
        return photo;
    }

    private ProductPhotoCandidateRequest candidate(String image, String barcode) {
        return new ProductPhotoCandidateRequest(
                image, SOURCE, "Open Food Facts", "Test grocery pack",
                "Test Brand", "1 kg", "CC BY-SA",
                "Open Food Facts contributors", null, barcode);
    }
}
