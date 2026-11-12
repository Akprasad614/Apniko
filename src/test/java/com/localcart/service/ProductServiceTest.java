package com.localcart.service;

import com.localcart.entity.Product;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductService(productRepository);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptsAndTrimsHttpsProductImageUrl() {
        Product product = new Product();
        product.setName("Basmati Rice");
        product.setImageUrl("  https://cdn.example.com/products/rice.webp  ");

        Product saved = productService.createProduct(product);

        assertEquals("https://cdn.example.com/products/rice.webp", saved.getImageUrl());
        verify(productRepository).save(product);
    }

    @Test
    void blankImageUrlIsStoredAsNull() {
        Product product = new Product();
        product.setName("Tomato");
        product.setImageUrl("  ");

        Product saved = productService.createProduct(product);

        assertNull(saved.getImageUrl());
    }

    @Test
    void rejectsNonHttpImageUrl() {
        Product product = new Product();
        product.setName("Milk");
        product.setImageUrl("javascript:alert(1)");

        assertThrows(BadRequestException.class,
                () -> productService.createProduct(product));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void rejectsMalformedImageUrl() {
        Product product = new Product();
        product.setName("Bread");
        product.setImageUrl("not-a-url");

        assertThrows(BadRequestException.class,
                () -> productService.createProduct(product));
    }
}
