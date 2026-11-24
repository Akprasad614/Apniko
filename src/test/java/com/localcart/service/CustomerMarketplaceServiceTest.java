package com.localcart.service;

import com.localcart.entity.Shop;
import com.localcart.entity.ProductCatalogueReviewStatus;
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

import java.util.Arrays;
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
        assertEquals("Local Fresh", result.get(0).name());
        assertTrue(result.get(0).active());
        assertEquals(
                List.of("id", "name", "address", "active", "deliveryMode",
                        "latitude", "longitude", "distanceKm"),
                Arrays.stream(result.get(0).getClass().getRecordComponents())
                        .map(component -> component.getName())
                        .toList()
        );
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
                eq(ProductCatalogueReviewStatus.ACTIVE), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(6);
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
                eq(ProductCatalogueReviewStatus.ACTIVE),
                argThat(pageable -> pageable.getPageNumber() == 2
                        && pageable.getPageSize() == 15
                        && pageable.getSort().isSorted())
        );
    }


    @Test
    void searchWithoutShopIdQueriesAcrossActiveShops() {
        when(inventoryRepository.searchMarketplace(
                eq("milk"), isNull(), isNull(), isNull(), isNull(),
                eq(ProductCatalogueReviewStatus.ACTIVE), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(), invocation.getArgument(6), 0));

        Page<?> result = service.searchProducts(
                " milk ", null, null, null, null,
                0, 8, "name", "asc");

        assertTrue(result.isEmpty());
        verify(inventoryRepository).searchMarketplace(
                eq("milk"), isNull(), isNull(), isNull(), isNull(),
                eq(ProductCatalogueReviewStatus.ACTIVE),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 8)
        );
        verifyNoInteractions(shopRepository);
    }

    @Test
    void priceComparisonRejectsUnknownProduct() {
        when(productRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.compareProductPrices(999L));
        verify(inventoryRepository, never()).findAvailablePricesForProduct(
                anyLong(), any(ProductCatalogueReviewStatus.class));
    }

    @Test
    void priceComparisonUsesOnlyAvailableShopInventory() {
        when(productRepository.existsById(101L)).thenReturn(true);
        when(inventoryRepository.findAvailablePricesForProduct(
                101L, ProductCatalogueReviewStatus.ACTIVE))
                .thenReturn(List.of());

        assertTrue(service.compareProductPrices(101L).isEmpty());
        verify(inventoryRepository).findAvailablePricesForProduct(
                101L, ProductCatalogueReviewStatus.ACTIVE);
    }

    @Test
    void nearbyShopsAreSortedByDistanceAndExcludeInactiveUnpinnedAndDistantShops() {
        Shop near = shop(1L, "Near Store", true);
        when(near.getLatitude()).thenReturn(12.9716);
        when(near.getLongitude()).thenReturn(77.5946);

        Shop farther = shop(2L, "Further Store", true);
        when(farther.getLatitude()).thenReturn(12.9816);
        when(farther.getLongitude()).thenReturn(77.5946);

        Shop noPin = shop(3L, "Unpinned Store", true);
        when(noPin.getLatitude()).thenReturn(null);
        when(noPin.getLongitude()).thenReturn(null);

        Shop inactive = shop(4L, "Inactive Store", false);
        when(inactive.getLatitude()).thenReturn(12.9716);
        when(inactive.getLongitude()).thenReturn(77.5946);

        Shop outsideRadius = shop(5L, "Outside Radius", true);
        when(outsideRadius.getLatitude()).thenReturn(13.10);
        when(outsideRadius.getLongitude()).thenReturn(77.5946);

        when(shopRepository.findAll()).thenReturn(
                List.of(outsideRadius, farther, noPin, inactive, near));

        var result = service.getNearbyShops(12.9716, 77.5946, 5.0);

        assertEquals(List.of(1L, 2L), result.stream().map(item -> item.id()).toList());
        assertEquals(0.0, result.get(0).distanceKm());
        assertTrue(result.get(1).distanceKm() > result.get(0).distanceKm());
    }

    @Test
    void nearbySearchRejectsInvalidCoordinatesAndRadius() {
        assertThrows(BadRequestException.class,
                () -> service.getNearbyShops(91.0, 77.5, 10.0));
        assertThrows(BadRequestException.class,
                () -> service.getNearbyShops(12.9, 181.0, 10.0));
        assertThrows(BadRequestException.class,
                () -> service.getNearbyShops(12.9, 77.5, 51.0));
        verifyNoInteractions(shopRepository);
    }

    private Shop shop(Long id, String name, boolean active) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getName()).thenReturn(name);
        when(shop.isActive()).thenReturn(active);
        when(shop.getAddress()).thenReturn("Main Road");
        when(shop.getDeliveryMode()).thenReturn(com.localcart.entity.DeliveryMode.SHOP_DELIVERY);
        return shop;
    }
}
