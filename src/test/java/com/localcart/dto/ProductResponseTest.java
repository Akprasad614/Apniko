package com.localcart.dto;

import com.localcart.entity.Product;
import com.localcart.entity.ProductImageReviewStatus;
import com.localcart.entity.ProductPhoto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
