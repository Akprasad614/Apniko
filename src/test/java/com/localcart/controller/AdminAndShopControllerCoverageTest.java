package com.localcart.controller;

import com.localcart.dto.AssignShopOwnerRequest;
import com.localcart.dto.CreateManagedUserRequest;
import com.localcart.dto.CreateShopRequest;
import com.localcart.dto.UpdateActiveStatusRequest;
import com.localcart.dto.UpdateShopRequest;
import com.localcart.entity.Role;
import com.localcart.security.SecurityUtils;
import com.localcart.service.AdminService;
import com.localcart.service.ShopService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAndShopControllerCoverageTest {

    private AdminService adminService;
    private AdminController adminController;
    private ShopService shopService;
    private ShopController shopController;
    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        adminController = new AdminController(adminService);
        shopService = mock(ShopService.class);
        shopController = new ShopController(shopService);
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("admin@example.com");
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void adminUserManagementEndpointsDelegateAndCreateWithCreatedStatus() {
        CreateManagedUserRequest request = new CreateManagedUserRequest();
        assertEquals(201, adminController.createShopkeeper(request).getStatusCode().value());
        assertEquals(201, adminController.createDeliveryPartner(request).getStatusCode().value());
        assertEquals(200, adminController.getUsers(Role.CUSTOMER).getStatusCode().value());
        assertEquals(200, adminController.getUsers(null).getStatusCode().value());
        assertEquals(200, adminController.getUser(9L).getStatusCode().value());

        verify(adminService).createShopkeeper(request);
        verify(adminService).createDeliveryPartner(request);
        verify(adminService).getUsers(Role.CUSTOMER);
        verify(adminService).getUsers(null);
        verify(adminService).getUser(9L);
    }

    @Test
    void adminAccountAndShopManagementEndpointsDelegate() {
        UpdateActiveStatusRequest activeRequest = new UpdateActiveStatusRequest();
        activeRequest.setActive(false);
        AssignShopOwnerRequest ownerRequest = new AssignShopOwnerRequest();
        ownerRequest.setOwnerId(12L);

        assertEquals(200, adminController.updateUserActiveStatus(12L, activeRequest)
                .getStatusCode().value());
        assertEquals(200, adminController.getShops().getStatusCode().value());
        assertEquals(200, adminController.assignShopOwner(3L, ownerRequest)
                .getStatusCode().value());
        assertEquals(200, adminController.updateShopActiveStatus(3L, activeRequest)
                .getStatusCode().value());

        verify(adminService).updateUserActiveStatus(12L, activeRequest, "admin@example.com");
        verify(adminService).getShops();
        verify(adminService).assignShopOwner(3L, ownerRequest);
        verify(adminService).updateShopActiveStatus(3L, activeRequest);
    }

    @Test
    void shopControllerDelegatesShopCreationAndReadEndpoints() {
        CreateShopRequest request = new CreateShopRequest();

        assertEquals(200, shopController.createShop(request).getStatusCode().value());
        assertEquals(200, shopController.getAllShops().getStatusCode().value());
        assertEquals(200, shopController.getShopById(7L).getStatusCode().value());
        assertEquals(200, shopController.getShopsByOwner(12L).getStatusCode().value());

        verify(shopService).createShop("admin@example.com", request);
        verify(shopService).getAllShops();
        verify(shopService).getShopById(7L);
        verify(shopService).getShopsByOwner(12L);
    }

    @Test
    void shopControllerDelegatesUpdateAndDeactivation() {
        UpdateShopRequest request = new UpdateShopRequest();

        assertEquals(200, shopController.updateShop(7L, request).getStatusCode().value());
        assertEquals(200, shopController.deactivateShop(7L).getStatusCode().value());
        verify(shopService).updateShop(7L, "admin@example.com", request);
        verify(shopService).deactivateShop(7L, "admin@example.com");
    }
}
