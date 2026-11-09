package com.localcart.controller;

import com.localcart.entity.Order;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderControllerAuthorizationTest {

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
        controller = new OrderController(
                orderService, orderRepository, userRepository, shopRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() {
        User currentCustomer = activeUser(10L, Role.CUSTOMER);
        User differentCustomer = mock(User.class);
        when(differentCustomer.getId()).thenReturn(20L);
        Order order = mock(Order.class);
        when(order.getCustomer()).thenReturn(differentCustomer);
        when(orderRepository.findById(501L)).thenReturn(Optional.of(order));
        authenticate("customer@test.com", Role.CUSTOMER);
        when(userRepository.findByEmail("customer@test.com"))
                .thenReturn(Optional.of(currentCustomer));

        ResponseEntity<Order> response = controller.getOrderById(501L);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void customerCanReadTheirOwnOrder() {
        User currentCustomer = activeUser(10L, Role.CUSTOMER);
        Order order = mock(Order.class);
        when(order.getCustomer()).thenReturn(currentCustomer);
        when(orderRepository.findById(501L)).thenReturn(Optional.of(order));
        authenticate("customer@test.com", Role.CUSTOMER);
        when(userRepository.findByEmail("customer@test.com"))
                .thenReturn(Optional.of(currentCustomer));

        ResponseEntity<Order> response = controller.getOrderById(501L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(order, response.getBody());
    }

    @Test
    void shopkeeperCannotReadAnotherShopsOrder() {
        User currentShopkeeper = activeUser(12L, Role.SHOPKEEPER);
        User otherOwner = mock(User.class);
        when(otherOwner.getId()).thenReturn(99L);
        Shop shop = mock(Shop.class);
        when(shop.getOwner()).thenReturn(otherOwner);
        Order order = mock(Order.class);
        when(order.getShop()).thenReturn(shop);
        when(orderRepository.findById(502L)).thenReturn(Optional.of(order));
        authenticate("shopkeeper@test.com", Role.SHOPKEEPER);
        when(userRepository.findByEmail("shopkeeper@test.com"))
                .thenReturn(Optional.of(currentShopkeeper));

        ResponseEntity<Order> response = controller.getOrderById(502L);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void assignedDeliveryPartnerCanReadTheirOrder() {
        User partner = activeUser(15L, Role.DELIVERY_PARTNER);
        Order order = mock(Order.class);
        when(order.getDeliveryPartner()).thenReturn(partner);
        when(orderRepository.findById(503L)).thenReturn(Optional.of(order));
        authenticate("delivery@test.com", Role.DELIVERY_PARTNER);
        when(userRepository.findByEmail("delivery@test.com"))
                .thenReturn(Optional.of(partner));

        ResponseEntity<Order> response = controller.getOrderById(503L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(order, response.getBody());
    }

    @Test
    void adminCanReadAnyOrder() {
        User admin = activeUser(13L, Role.ADMIN);
        Order order = mock(Order.class);
        when(orderRepository.findById(504L)).thenReturn(Optional.of(order));
        authenticate("admin@test.com", Role.ADMIN);
        when(userRepository.findByEmail("admin@test.com"))
                .thenReturn(Optional.of(admin));

        ResponseEntity<Order> response = controller.getOrderById(504L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(order, response.getBody());
    }

    private User activeUser(Long id, Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(true);
        return user;
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
