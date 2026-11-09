package com.localcart.controller;

import com.localcart.entity.Order;
import com.localcart.security.SecurityUtils;
import com.localcart.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryOrderControllerCoverageTest {

    private OrderService orderService;
    private DeliveryOrderController controller;
    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        controller = new DeliveryOrderController(orderService);
        securityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void availableQueueAndAssignedOrdersRequireAuthentication() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);

        assertEquals(401, controller.getAvailableOrders().getStatusCode().value());
        assertEquals(401, controller.getMyOrders().getStatusCode().value());
        verifyNoInteractions(orderService);
    }

    @Test
    void authenticatedPartnerCanReadAvailableAndAssignedOrders() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("partner@example.com");
        when(orderService.getAvailableDeliveryOrders()).thenReturn(List.of(new Order()));
        when(orderService.getMyDeliveryOrders("partner@example.com")).thenReturn(List.of());

        assertEquals(1, controller.getAvailableOrders().getBody().size());
        assertTrue(controller.getMyOrders().getBody().isEmpty());
        verify(orderService).getMyDeliveryOrders("partner@example.com");
    }

    @Test
    void acceptReturnsOrderWhenServiceSucceeds() {
        Order order = new Order();
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("partner@example.com");
        when(orderService.acceptDeliveryOrder(10L, "partner@example.com")).thenReturn(order);

        var response = controller.acceptOrder(10L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(order, response.getBody());
    }

    @Test
    void acceptReturnsBadRequestWhenServiceRejectsOrder() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("partner@example.com");
        when(orderService.acceptDeliveryOrder(11L, "partner@example.com"))
                .thenThrow(new IllegalStateException("Order cannot be accepted"));

        assertEquals(400, controller.acceptOrder(11L).getStatusCode().value());
    }

    @Test
    void pickupReturnsBadRequestWhenServiceRejectsTransition() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("partner@example.com");
        when(orderService.pickupOrder(12L, "partner@example.com"))
                .thenThrow(new IllegalArgumentException("Order is not ready"));

        assertEquals(400, controller.pickupOrder(12L).getStatusCode().value());
    }

    @Test
    void deliverReturnsOrderWhenServiceSucceedsAndBadRequestWhenItFails() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("partner@example.com");
        Order order = new Order();
        when(orderService.deliverOrder(13L, "partner@example.com")).thenReturn(order);
        assertEquals(200, controller.deliverOrder(13L).getStatusCode().value());

        when(orderService.deliverOrder(14L, "partner@example.com"))
                .thenThrow(new IllegalStateException("Not assigned"));
        assertEquals(400, controller.deliverOrder(14L).getStatusCode().value());
    }

    @Test
    void acceptPickupAndDeliverReturnUnauthorizedWithoutEmail() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);
        assertEquals(401, controller.acceptOrder(1L).getStatusCode().value());
        assertEquals(401, controller.pickupOrder(1L).getStatusCode().value());
        assertEquals(401, controller.deliverOrder(1L).getStatusCode().value());
        verifyNoInteractions(orderService);
    }
}
