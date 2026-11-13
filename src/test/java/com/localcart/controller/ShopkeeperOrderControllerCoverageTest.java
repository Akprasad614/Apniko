package com.localcart.controller;

import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import com.localcart.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopkeeperOrderControllerCoverageTest {

    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private OrderService orderService;
    private ShopkeeperOrderController controller;
    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        orderService = mock(OrderService.class);
        controller = new ShopkeeperOrderController(orderRepository, userRepository, orderService);
        securityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void orderListRequiresAnAuthenticatedShopkeeper() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);
        assertEquals(401, controller.getMyOrders().getStatusCode().value());

        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(10L, Role.CUSTOMER, true)));
        assertEquals(401, controller.getMyOrders().getStatusCode().value());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void activeShopkeeperReceivesOnlyTheirOrderList() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.findByShopOwnerId(12L)).thenReturn(List.of(new Order()));

        var response = controller.getMyOrders();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        verify(orderRepository).findByShopOwnerId(12L);
    }

    @Test
    void individualOrderReturnsNotFoundOrForbiddenWhenNotOwned() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(404, controller.getMyOrder(1L).getStatusCode().value());

        Order order = new Order();
        Shop shop = mock(Shop.class);
        when(shop.getOwner()).thenReturn(user(99L, Role.SHOPKEEPER, true));
        order.setShop(shop);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
        assertEquals(403, controller.getMyOrder(2L).getStatusCode().value());
    }

    @Test
    void individualOrderIsVisibleToItsOwningShopkeeper() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        Order order = new Order();
        Shop shop = mock(Shop.class);
        when(shop.getOwner()).thenReturn(owner);
        order.setShop(shop);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        var response = controller.getMyOrder(3L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(order, response.getBody());
    }

    @Test
    void statusUpdateReturnsBadRequestWhenServiceRejectsTransition() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        Order order = new Order();
        Shop shop = mock(Shop.class);
        when(shop.getOwner()).thenReturn(owner);
        order.setShop(shop);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.findById(4L)).thenReturn(Optional.of(order));
        when(orderService.updateShopkeeperOrderStatus(4L, OrderStatus.READY, "owner@example.com"))
                .thenReturn(Optional.empty());

        assertEquals(400, controller.updateOrderStatus(4L, OrderStatus.READY)
                .getStatusCode().value());
    }

    @Test
    void statusUpdateReturnsNotFoundWhenOrderIsMissing() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        assertEquals(404, controller.updateOrderStatus(5L, OrderStatus.ACCEPTED)
                .getStatusCode().value());
        verifyNoInteractions(orderService);
    }

    @Test
    void rejectEndpointDelegatesToLockedShopkeeperRejection() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        Order order = new Order();
        order.setStatus(OrderStatus.PLACED);
        Shop shop = mock(Shop.class);
        when(shop.getOwner()).thenReturn(owner);
        order.setShop(shop);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderService.rejectOrderByShopkeeper(7L, "owner@example.com", "Out of stock"))
                .thenReturn(Optional.of(order));

        var response = controller.rejectOrder(7L, cancelRequest("Out of stock"));

        assertEquals(200, response.getStatusCode().value());
        verify(orderService).rejectOrderByShopkeeper(7L, "owner@example.com", "Out of stock");
    }

    private static com.localcart.dto.CancelOrderRequest cancelRequest(String reason) {
        com.localcart.dto.CancelOrderRequest request = new com.localcart.dto.CancelOrderRequest();
        request.setReason(reason);
        return request;
    }

    @Test
    void cancelEndpointReturnsBadRequestWhenCancellationIsRejected() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        Order order = new Order();
        Shop shop = mock(Shop.class);
        when(shop.getOwner()).thenReturn(owner);
        order.setShop(shop);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.findById(6L)).thenReturn(Optional.of(order));
        when(orderService.cancelOrderByShopkeeper(6L, "owner@example.com", "Cancelled by shopkeeper"))
                .thenReturn(Optional.empty());

        assertEquals(400, controller.cancelOrder(6L, null).getStatusCode().value());
    }

    private static User user(Long id, Role role, boolean active) {
        User user = new User();
        user.setName("Test " + role.name());
        user.setEmail(role == Role.SHOPKEEPER
                ? "owner@example.com" : "customer@example.com");
        user.setRole(role);
        user.setActive(active);
        setEntityId(user, id);
        return user;
    }

    private static void setEntityId(Object entity, Long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to assign test entity ID", exception);
        }
    }
}
