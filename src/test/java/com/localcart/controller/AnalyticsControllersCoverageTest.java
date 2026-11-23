package com.localcart.controller;

import com.localcart.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsControllersCoverageTest {

    private AnalyticsService service;
    private AdminAnalyticsController adminController;
    private ShopkeeperAnalyticsController shopkeeperController;

    @BeforeEach
    void setUp() {
        service = mock(AnalyticsService.class);
        adminController = new AdminAnalyticsController(service);
        shopkeeperController = new ShopkeeperAnalyticsController(service);
    }

    @Test
    void adminAnalyticsEndpointsDelegateDateFiltersAndLimit() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        assertEquals(200, adminController.getOverview(from, to).getStatusCode().value());
        assertEquals(200, adminController.getDaily(from, to).getStatusCode().value());
        assertEquals(200, adminController.getTopShops(from, to, 7).getStatusCode().value());
        assertEquals(200, adminController.getTopProducts(from, to, 8).getStatusCode().value());

        verify(service).getAdminOverview(from, to);
        verify(service).getAdminDaily(from, to);
        verify(service).getAdminTopShops(from, to, 7);
        verify(service).getAdminTopProducts(from, to, 8);
    }

    @Test
    void shopkeeperAnalyticsEndpointsDelegateDateFiltersAndOptionalShop() {
        LocalDate from = LocalDate.of(2026, 2, 1);
        LocalDate to = LocalDate.of(2026, 2, 28);

        assertEquals(200, shopkeeperController.getOverview(from, to).getStatusCode().value());
        assertEquals(200, shopkeeperController.getDaily(from, to).getStatusCode().value());
        assertEquals(200, shopkeeperController.getTopProducts(4L, from, to, 5)
                .getStatusCode().value());
        assertEquals(200, shopkeeperController.getTopProducts(null, null, null, 10)
                .getStatusCode().value());

        verify(service).getShopkeeperOverview(from, to);
        verify(service).getShopkeeperDaily(from, to);
        verify(service).getShopkeeperTopProducts(4L, from, to, 5);
        verify(service).getShopkeeperTopProducts(null, null, null, 10);
    }
}
