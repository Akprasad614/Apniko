package com.localcart.service;

import com.localcart.entity.Shop;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ProductRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerMarketplaceServiceTest {

    private ShopRepository shopRepository;
    private ShopInventoryRepository inventoryRepository;
    private ProductRepository productRepository;
    private CustomerMarketplaceService service;

    @BeforeEach
    void setUp() {
        shopRepository = mock(ShopRepository.class);
        inventoryRepository = mock(ShopInventoryRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new CustomerMarketplaceService(
                shopRepository, inventoryRepository, productRepository
        );
    }

    @Test
    void activeShopListingExcludesInactiveShops() {
        Shop active = shop(1L, "Local Fresh", true);
        Shop inactive = shop(2L, "Closed Store", false);
        when(shopRepository.findAll()).thenReturn(List.of(active, inactive));

        var result = service.getActiveShops();

        assertEquals(1, result.size());
        assertEquals("Local Fresh", result.get(0).getName());
        assertTrue(result.get(0).isActive());
    }

    @Test
    void inactiveShopCannotBeOpened() {
        // Build the mock before starting the repository stubbing. Stubbing a
        // mock from inside thenReturn(...) causes Mockito unfinished stubbing.
        Shop inactive = shop(2L, "Closed Store", false);
        when(shopRepository.findById(2L)).thenReturn(Optional.of(inactive));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getActiveShop(2L));
    }

    @Test
    void searchRejectsNegativePage() {
        assertThrows(BadRequestException.class,
                () -> service.searchProducts("rice", null, null,
                        null, null, -1, 20, "name", "asc"));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void searchRejectsPageSizeAboveMaximum() {
        assertThrows(BadRequestException.class,
                () -> service.searchProducts(null, null, null,
                        null, null, 0, 101, "name", "asc"));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void searchRejectsMinimumPriceGreaterThanMaximumPrice() {
        assertThrows(BadRequestException.class,
                () -> service.searchProducts(null, null, null,
                        100.0, 50.0, 0, 20, "price", "asc"));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void searchRejectsUnsupportedSortField() {
        assertThrows(BadRequestException.class,
                () -> service.searchProducts(null, null, null,
                        null, null, 0, 20, "unknown", "asc"));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void searchNormalizesFiltersAndBuildsRequestedPage() {
        // The service validates the requested shop before querying inventory.
        Shop activeShop = shop(1L, "Local Fresh", true);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(activeShop));
        when(inventoryRepository.searchMarketplace(
                eq("rice"), eq("Staples"), eq(1L), eq(10.0), eq(500.0),
                any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(5);
                    return new PageImpl<>(List.of(), pageable, 0);
                });

        Page<?> result = service.searchProducts(
                "  rice  ", " Staples ", 1L,
                10.0, 500.0, 2, 15, "price", "desc"
        );

        assertTrue(result.isEmpty());
        verify(shopRepository).findById(1L);
        verify(inventoryRepository).searchMarketplace(
                eq("rice"), eq("Staples"), eq(1L), eq(10.0), eq(500.0),
                argThat(pageable -> pageable.getPageNumber() == 2
                        && pageable.getPageSize() == 15
                        && pageable.getSort().isSorted())
        );
    }

    @Test
    void priceComparisonRejectsUnknownProduct() {
        when(productRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.compareProductPrices(999L));
        verify(inventoryRepository, never()).findAvailablePricesForProduct(anyLong());
    }

    @Test
    void priceComparisonUsesOnlyAvailableShopInventory() {
        when(productRepository.existsById(101L)).thenReturn(true);
        when(inventoryRepository.findAvailablePricesForProduct(101L))
                .thenReturn(List.of());

        assertTrue(service.compareProductPrices(101L).isEmpty());
        verify(inventoryRepository).findAvailablePricesForProduct(101L);
    }

    @Test
    void marketplaceSearchIncludesProductImageAndShopAddress() {
        com.localcart.entity.Product product = mock(com.localcart.entity.Product.class);
        when(product.getId()).thenReturn(101L);
        when(product.getName()).thenReturn("Fresh Apples");
        when(product.getCategory()).thenReturn("Fruits");
        when(product.getImageUrl()).thenReturn("https://cdn.example.com/apples.webp");

        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(8L);
        when(shop.getName()).thenReturn("Fresh Basket");
        when(shop.getAddress()).thenReturn("HSR Layout, Bengaluru");

        com.localcart.entity.ShopInventory inventory =
                mock(com.localcart.entity.ShopInventory.class);
        when(inventory.getId()).thenReturn(55L);
        when(inventory.getProduct()).thenReturn(product);
        when(inventory.getShop()).thenReturn(shop);
        when(inventory.getPrice()).thenReturn(120.0);
        when(inventory.getStockQuantity()).thenReturn(9);
        when(inventory.isAvailable()).thenReturn(true);

        when(inventoryRepository.searchMarketplace(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inventory)));

        Page<com.localcart.dto.InventoryResponse> result = service.searchProducts(
                null, null, null, null, null, 0, 24, "name", "asc"
        );

        assertEquals(1, result.getContent().size());
        assertEquals("https://cdn.example.com/apples.webp",
                result.getContent().get(0).getImageUrl());
        assertEquals("HSR Layout, Bengaluru",
                result.getContent().get(0).getShopAddress());
        assertEquals("Fresh Apples", result.getContent().get(0).getProductName());
    }

    private Shop shop(Long id, String name, boolean active) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getName()).thenReturn(name);
        when(shop.isActive()).thenReturn(active);
        return shop;
    }
}
