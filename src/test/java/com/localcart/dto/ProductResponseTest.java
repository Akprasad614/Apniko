package com.localcart.dto;

import com.localcart.entity.Product;
import com.localcart.entity.ProductImageReviewStatus;
import com.localcart.entity.ProductPhoto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductResponseTest {

    @Test
    void includesOnlyApprovedProductPhotoInShopkeeperCatalogueResponse() {
        Product product = new Product();
        product.setName("Aashirvaad Atta");
        product.setBrand("Aashirvaad");
        product.setCategory("Foodgrains, Oil & Masala");
        product.setPackSize("5 kg");

        ProductPhoto photo = new ProductPhoto();
        photo.setApprovedImageUrl("https://cdn.example.test/aashirvaad-atta-5kg.jpg");
        photo.setSourceName("Manufacturer-authorised catalogue");
        photo.setSourceUrl("https://brand.example/products/aashirvaad-atta");
        photo.setLicense("MANUFACTURER_AUTHORIZED");
        photo.setAttribution("Image supplied by Aashirvaad");
        photo.setReviewStatus(ProductImageReviewStatus.APPROVED);
        product.setPhoto(photo);

        ProductResponse response = ProductResponse.from(product);

        assertEquals("Aashirvaad Atta", response.getName());
        assertEquals("Aashirvaad", response.getBrand());
        assertEquals("5 kg", response.getPackSize());
        assertEquals("https://cdn.example.test/aashirvaad-atta-5kg.jpg", response.getImageUrl());
        assertEquals("https://brand.example/products/aashirvaad-atta", response.getImageSourceUrl());
        assertEquals("MANUFACTURER_AUTHORIZED", response.getImageLicense());
        assertEquals("Image supplied by Aashirvaad", response.getImageAttribution());
    }

    @Test
    void rawOrderProductProjectionExposesOnlyApprovedPhotoMetadata() throws JsonProcessingException {
        Product product = new Product();
        product.setName("Aashirvaad Atta");

        ProductPhoto photo = new ProductPhoto();
        photo.setApprovedImageUrl("https://cdn.example.test/approved.jpg");
        photo.setSourceName("Open Food Facts");
        photo.setSourceUrl("https://world.openfoodfacts.org/product/8900000000000");
        photo.setLicense("CC BY-SA");
        photo.setAttribution("Open Food Facts contributors");
        photo.setCandidateImageUrl("https://cdn.example.test/pending.jpg");
        photo.setCandidateAuthorizationReference("PRIVATE-SUPPLIER-AGREEMENT");
        product.setPhoto(photo);

        String json = new ObjectMapper().writeValueAsString(product);

        assertTrue(json.contains("\"imageUrl\":\"https://cdn.example.test/approved.jpg\""));
        assertTrue(json.contains("\"imageLicense\":\"CC BY-SA\""));
        assertTrue(json.contains("\"imageAttribution\":\"Open Food Facts contributors\""));
        assertFalse(json.contains("candidateImageUrl"));
        assertFalse(json.contains("pending.jpg"));
        assertFalse(json.contains("PRIVATE-SUPPLIER-AGREEMENT"));
        assertFalse(json.contains("\"photo\""));
    }

    @Test
    void doesNotExposeAnyPhotoMetadataWhenThereIsNoApprovedImage() throws JsonProcessingException {
        Product product = new Product();
        product.setName("Aashirvaad Atta");

        ProductPhoto photo = new ProductPhoto();
        photo.setCandidateImageUrl("https://cdn.example.test/pending.jpg");
        photo.setSourceName("Private pending source");
        product.setPhoto(photo);

        String json = new ObjectMapper().writeValueAsString(product);

        assertTrue(json.contains("\"imageUrl\":null"));
        assertTrue(json.contains("\"imageSourceName\":null"));
        assertFalse(json.contains("Private pending source"));
        assertFalse(json.contains("pending.jpg"));
        assertFalse(json.contains("\"photo\""));
    }

    @Test
    void keepsPreviouslyApprovedPhotoVisibleWhileReplacementIsPending() {
        Product product = new Product();
        product.setName("Aashirvaad Atta");

        ProductPhoto photo = new ProductPhoto();
        photo.setApprovedImageUrl("https://cdn.example.test/approved-old-pack.jpg");
        photo.setCandidateImageUrl("https://cdn.example.test/pending-new-pack.jpg");
        photo.setReviewStatus(ProductImageReviewStatus.PENDING);
        product.setPhoto(photo);

        ProductResponse response = ProductResponse.from(product);

        assertEquals("https://cdn.example.test/approved-old-pack.jpg", response.getImageUrl());
    }

    @Test
    void doesNotExposePendingCandidatePhotoInShopkeeperCatalogueResponse() {
        Product product = new Product();
        product.setName("Aashirvaad Atta");

        ProductPhoto photo = new ProductPhoto();
        photo.setCandidateImageUrl("https://cdn.example.test/pending.jpg");
        photo.setReviewStatus(ProductImageReviewStatus.PENDING);
        product.setPhoto(photo);

        ProductResponse response = ProductResponse.from(product);

        assertNull(response.getImageUrl());
        assertNull(response.getImageSourceName());
    }

    @Test
    void doesNotExposeRejectedCandidatePhotoInShopkeeperCatalogueResponse() {
        Product product = new Product();
        product.setName("Aashirvaad Atta");

        ProductPhoto photo = new ProductPhoto();
        photo.setCandidateImageUrl("https://cdn.example.test/rejected.jpg");
        photo.setReviewStatus(ProductImageReviewStatus.REJECTED);
        product.setPhoto(photo);

        ProductResponse response = ProductResponse.from(product);

        assertNull(response.getImageUrl());
    }
}
