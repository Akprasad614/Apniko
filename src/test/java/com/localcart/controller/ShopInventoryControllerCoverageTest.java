package com.localcart.controller;

import com.localcart.dto.AddInventoryRequest;
import com.localcart.dto.RestockInventoryRequest;
import com.localcart.dto.UpdateInventoryRequest;
import com.localcart.dto.UpdateLowStockThresholdRequest;
import com.localcart.security.SecurityUtils;
import com.localcart.service.ShopInventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopInventoryControllerCoverageTest {

    private ShopInventoryService service;
    private ShopInventoryController controller;
    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        service = mock(ShopInventoryService.class);
        controller = new ShopInventoryController(service);
        securityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void inventoryEndpointsReturnUnauthorizedWhenNoCurrentEmailExists() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);

        assertEquals(401, controller.addInventory(1L, new AddInventoryRequest()).getStatusCode().value());
        assertEquals(401, controller.getMyInventory(1L).getStatusCode().value());
        assertEquals(401, controller.updateInventory(2L, new UpdateInventoryRequest()).getStatusCode().value());
        assertEquals(401, controller.restock(1L, 2L, new RestockInventoryRequest()).getStatusCode().value());
        assertEquals(401, controller.updateLowStockThreshold(
                1L, 2L, new UpdateLowStockThresholdRequest()).getStatusCode().value());
        assertEquals(401, controller.getLowStockItems(1L).getStatusCode().value());
        assertEquals(401, controller.getMovementHistory(1L, 2L).getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void addInventoryForwardsCurrentEmailShopIdAndRequest() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        AddInventoryRequest request = new AddInventoryRequest();

        assertEquals(200, controller.addInventory(5L, request).getStatusCode().value());
        verify(service).addInventory("owner@example.com", 5L, request);
    }

    @Test
    void getInventoryAndLowStockEndpointsDelegateToService() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");

        assertEquals(200, controller.getMyInventory(5L).getStatusCode().value());
        assertEquals(200, controller.getLowStockItems(5L).getStatusCode().value());
        verify(service).getMyInventory("owner@example.com", 5L);
        verify(service).getLowStockItems("owner@example.com", 5L);
    }

    @Test
    void updateInventoryForwardsInventoryIdAndRequest() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        UpdateInventoryRequest request = new UpdateInventoryRequest();

        assertEquals(200, controller.updateInventory(22L, request).getStatusCode().value());
        verify(service).updateInventory("owner@example.com", 22L, request);
    }

    @Test
    void restockForwardsShopAndInventoryIds() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        RestockInventoryRequest request = new RestockInventoryRequest();

        assertEquals(200, controller.restock(5L, 22L, request).getStatusCode().value());
        verify(service).restock("owner@example.com", 5L, 22L, request);
    }

    @Test
    void thresholdUpdateForwardsShopAndInventoryIds() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        UpdateLowStockThresholdRequest request = new UpdateLowStockThresholdRequest();

        assertEquals(200, controller.updateLowStockThreshold(5L, 22L, request)
                .getStatusCode().value());
        verify(service).updateLowStockThreshold("owner@example.com", 5L, 22L, request);
    }

    @Test
    void movementHistoryForwardsShopAndInventoryIds() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");

        assertEquals(200, controller.getMovementHistory(5L, 22L).getStatusCode().value());
        verify(service).getMovementHistory("owner@example.com", 5L, 22L);
    }
}
