package com.localcart.service;

import com.localcart.entity.Product;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @InjectMocks private ProductService service;

    @Test
    void createsProductWithStableIdentityWhenBarcodeIsValid() {
        Product product = new Product();
        product.setName("Sample Tea");
        product.setBrand("Sample Brand");
        product.setCategory("Beverages");
        product.setSubcategory("Tea, Coffee & Cocoa");
        product.setPackSize("250 g");
        product.setBarcode("4006381333931");
        product.setMrp(new BigDecimal("120.00"));
        product.setMrpSource("pack");

        when(productRepository.findByBarcode("4006381333931")).thenReturn(Optional.empty());
        when(productRepository.findByIdentityKey(anyString())).thenReturn(Optional.empty());
        when(productRepository.save(product)).thenReturn(product);

        Product saved = service.createProduct(product);

        assertSame(product, saved);
        assertNotNull(saved.getIdentityKey());
        assertEquals("pack", saved.getMrpSource());
        verify(productRepository).save(product);
    }

    @Test
    void rejectsInvalidBarcodeCheckDigit() {
        Product product = new Product();
        product.setName("Sample Tea");
        product.setBarcode("4006381333932");

        assertThrows(BadRequestException.class, () -> service.createProduct(product));
        verifyNoInteractions(productRepository);
    }

    @Test
    void rejectsExistingBarcode() {
        Product product = new Product();
        product.setName("Sample Tea");
        product.setBarcode("4006381333931");

        when(productRepository.findByBarcode("4006381333931"))
                .thenReturn(Optional.of(new Product()));

        assertThrows(BadRequestException.class, () -> service.createProduct(product));
        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateNaturalProductIdentityWithoutBarcode() {
        Product product = new Product();
        product.setName("Sample Tea");
        product.setBrand("Sample Brand");
        product.setPackSize("250 g");

        when(productRepository.findByIdentityKey(anyString()))
                .thenReturn(Optional.of(new Product()));

        assertThrows(BadRequestException.class, () -> service.createProduct(product));
        verify(productRepository, never()).save(any());
    }

    @Test
    void requiresAnApprovedMrpSource() {
        Product product = new Product();
        product.setName("Sample Tea");
        product.setMrp(new BigDecimal("120.00"));

        assertThrows(BadRequestException.class, () -> service.createProduct(product));
        verify(productRepository, never()).save(any());
    }
}
