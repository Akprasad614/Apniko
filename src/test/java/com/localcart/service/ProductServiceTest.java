package com.localcart.service;

import com.localcart.entity.Product;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        service = new ProductService(productRepository);
    }

    @Test
    void createProductKeepsOptionalHttpsImageUrl() {
        Product product = new Product();
        product.setName("Basmati rice");
        product.setImageUrl("  https://cdn.example.com/products/rice.jpg  ");
        when(productRepository.save(product)).thenReturn(product);

        Product saved = service.createProduct(product);

        assertEquals("https://cdn.example.com/products/rice.jpg", saved.getImageUrl());
        verify(productRepository).save(product);
    }

    @Test
    void createProductAllowsMissingImageUrlForExistingCatalogueWorkflows() {
        Product product = new Product();
        product.setName("Fresh tomatoes");
        when(productRepository.save(product)).thenReturn(product);

        assertNull(service.createProduct(product).getImageUrl());
    }

    @Test
    void createProductRejectsNonHttpsImageUrl() {
        Product product = new Product();
        product.setImageUrl("http://cdn.example.com/product.jpg");

        assertThrows(BadRequestException.class, () -> service.createProduct(product));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProductImageUpdatesAnExistingCatalogueItem() {
        Product product = new Product();
        product.setName("Paneer");
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Product updated = service.updateProductImage(
                7L, "https://cdn.example.com/products/paneer.webp");

        assertEquals("https://cdn.example.com/products/paneer.webp", updated.getImageUrl());
        verify(productRepository).save(product);
    }

    @Test
    void updateProductImageCanClearTheImage() {
        Product product = new Product();
        product.setImageUrl("https://cdn.example.com/products/paneer.webp");
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Product updated = service.updateProductImage(7L, " ");

        assertNull(updated.getImageUrl());
    }

    @Test
    void updateProductImageRejectsUnknownProduct() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateProductImage(404L, "https://cdn.example.com/p.jpg"));
        verify(productRepository, never()).save(any(Product.class));
    }
}
