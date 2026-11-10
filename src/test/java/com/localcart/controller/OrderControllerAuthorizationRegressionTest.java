package com.localcart.controller;

import com.localcart.entity.Order;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import com.localcart.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class OrderControllerAuthorizationRegressionTest {

    private OrderService orderService;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private ShopRepository shopRepository;
    private OrderController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        orderService = mock(OrderService.class);
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        shopRepository = mock(ShopRepository.class);
        controller = new OrderController(orderService, orderRepository, userRepository, shopRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCannotListAnotherCustomersOrders() {
        User customer = user(10L, Role.CUSTOMER, true);
        authenticate("customer@example.com", Role.CUSTOMER);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));

        ResponseEntity<List<Order>> response = controller.getOrdersByCustomer(20L);

        assertEquals(403, response.getStatusCode().value());
        verify(orderService, never()).getOrdersByCustomer(anyLong());
    }

    @Test
    void customerCanListOnlyTheirOwnOrders() {
        User customer = user(10L, Role.CUSTOMER, true);
        Order ownOrder = new Order();
        authenticate("customer@example.com", Role.CUSTOMER);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderService.getOrdersByCustomer(10L)).thenReturn(Optional.of(List.of(ownOrder)));

        ResponseEntity<List<Order>> response = controller.getOrdersByCustomer(10L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(ownOrder, response.getBody().get(0));
        verify(orderService).getOrdersByCustomer(10L);
    }

    @Test
    void shopkeeperCannotListAnotherShopsOrders() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        Shop ownShop = shop(1L, owner);
        authenticate("owner@example.com", Role.SHOPKEEPER);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.findByOwnerId(12L)).thenReturn(List.of(ownShop));

        ResponseEntity<List<Order>> response = controller.getOrdersByShop(2L);

        assertEquals(403, response.getStatusCode().value());
        verify(orderService, never()).getOrdersByShop(anyLong());
    }

    @Test
    void customerCannotUseShopOrderListingEndpoint() {
        User customer = user(10L, Role.CUSTOMER, true);
        authenticate("customer@example.com", Role.CUSTOMER);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));

        ResponseEntity<List<Order>> response = controller.getOrdersByShop(1L);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(orderService, shopRepository);
    }

    @Test
    void customerCannotCancelAnotherCustomersOrder() {
        User customer = user(10L, Role.CUSTOMER, true);
        User differentCustomer = user(20L, Role.CUSTOMER, true);
        Order order = mock(Order.class);
        when(order.getCustomer()).thenReturn(differentCustomer);
        authenticate("customer@example.com", Role.CUSTOMER);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findById(501L)).thenReturn(Optional.of(order));

        ResponseEntity<Order> response = controller.cancelMyOrder(501L, null);

        assertEquals(403, response.getStatusCode().value());
        verify(orderService, never()).cancelOrderByCustomer(anyLong(), anyString(), any());
    }

    @Test
    void customerCannotReadAnotherCustomersOrderHistory() {
        User customer = user(10L, Role.CUSTOMER, true);
        User differentCustomer = user(20L, Role.CUSTOMER, true);
        Order order = mock(Order.class);
        when(order.getCustomer()).thenReturn(differentCustomer);
        authenticate("customer@example.com", Role.CUSTOMER);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findById(502L)).thenReturn(Optional.of(order));

        var response = controller.getOrderHistory(502L);

        assertEquals(403, response.getStatusCode().value());
        verify(orderService, never()).getOrderStatusHistory(anyLong());
    }

    @Test
    void deliveryPartnerCannotReadAnOrderAssignedToSomeoneElse() {
        User currentPartner = user(15L, Role.DELIVERY_PARTNER, true);
        User assignedPartner = user(16L, Role.DELIVERY_PARTNER, true);
        Order order = mock(Order.class);
        when(order.getDeliveryPartner()).thenReturn(assignedPartner);
        authenticate("partner@example.com", Role.DELIVERY_PARTNER);
        when(userRepository.findByEmail("partner@example.com")).thenReturn(Optional.of(currentPartner));
        when(orderRepository.findById(503L)).thenReturn(Optional.of(order));

        ResponseEntity<Order> response = controller.getOrderById(503L);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void inactiveCustomerCannotReadAnOrder() {
        User inactiveCustomer = user(10L, Role.CUSTOMER, false);
        authenticate("inactive@example.com", Role.CUSTOMER);
        when(userRepository.findByEmail("inactive@example.com"))
                .thenReturn(Optional.of(inactiveCustomer));
        when(orderRepository.findById(504L)).thenReturn(Optional.of(new Order()));

        ResponseEntity<Order> response = controller.getOrderById(504L);

        assertEquals(401, response.getStatusCode().value());
        verify(orderRepository, never()).findById(504L);
    }

    private User user(Long id, Role role, boolean active) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(active);
        return user;
    }

    private Shop shop(Long id, User owner) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getOwner()).thenReturn(owner);
        return shop;
    }

    private void authenticate(String email, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email,
                        "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                )
        );
    }
}
