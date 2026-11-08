package com.localcart.controller;

import com.localcart.entity.Order;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import com.localcart.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;

    public OrderController(
            OrderService orderService,
            OrderRepository orderRepository,
            UserRepository userRepository,
            ShopRepository shopRepository) {

        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
    }

    // Customer creates an order
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Order> createOrder(
            @RequestBody com.localcart.dto.CreateOrderRequest request) {

        Optional<Order> order = orderService.createOrder(request);

        return order
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    // Only an admin can retrieve every order
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    // Customer, shopkeeper, assigned delivery partner, or admin
    // can retrieve an order only when they are authorized for it.
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(
            @PathVariable Long id) {

        User currentUser = getCurrentUser();

        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Order> orderOptional =
                orderRepository.findById(id);

        if (orderOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOptional.get();

        if (!canViewOrder(order, currentUser)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(order);
    }

    // Shopkeeper can retrieve orders only for their own shop.
    // Admin can retrieve orders for any shop.
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<Order>> getOrdersByShop(
            @PathVariable Long shopId) {

        User currentUser = getCurrentUser();

        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        if (currentUser.getRole() == Role.ADMIN) {
            return orderService.getOrdersByShop(shopId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() ->
                            ResponseEntity.notFound().build());
        }

        if (currentUser.getRole() != Role.SHOPKEEPER) {
            return ResponseEntity.status(403).build();
        }

        boolean ownsShop = shopRepository
                .findByOwnerId(currentUser.getId())
                .stream()
                .anyMatch(shop -> shop.getId().equals(shopId));

        if (!ownsShop) {
            return ResponseEntity.status(403).build();
        }

        return orderService.getOrdersByShop(shopId)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build());
    }

    // Customer can retrieve only their own orders.
    // Admin can retrieve orders for any customer.
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Order>> getOrdersByCustomer(
            @PathVariable Long customerId) {

        User currentUser = getCurrentUser();

        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        if (currentUser.getRole() != Role.ADMIN
                && (currentUser.getRole() != Role.CUSTOMER
                || !currentUser.getId().equals(customerId))) {

            return ResponseEntity.status(403).build();
        }

        return orderService.getOrdersByCustomer(customerId)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build());
    }

    private User getCurrentUser() {

        String email = SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return null;
        }

        return userRepository.findByEmail(email)
                .filter(User::isActive)
                .orElse(null);
    }

    private boolean canViewOrder(
            Order order,
            User currentUser) {

        if (currentUser.getRole() == Role.ADMIN) {
            return true;
        }

        if (currentUser.getRole() == Role.CUSTOMER) {
            return order.getCustomer() != null
                    && order.getCustomer().getId()
                    .equals(currentUser.getId());
        }

        if (currentUser.getRole() == Role.SHOPKEEPER) {
            return order.getShop() != null
                    && order.getShop().getOwner() != null
                    && order.getShop().getOwner().getId()
                    .equals(currentUser.getId());
        }

        if (currentUser.getRole() == Role.DELIVERY_PARTNER) {
            return order.getDeliveryPartner() != null
                    && order.getDeliveryPartner().getId()
                    .equals(currentUser.getId());
        }

        return false;
    }
}