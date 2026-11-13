package com.localcart.controller;

import com.localcart.dto.CancelOrderRequest;
import jakarta.validation.Valid;
import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import com.localcart.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/shopkeeper/orders")
public class ShopkeeperOrderController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;

    public ShopkeeperOrderController(
            OrderRepository orderRepository,
            UserRepository userRepository,
            OrderService orderService) {

        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<Order>> getMyOrders() {

        User shopkeeper = getCurrentShopkeeper();

        if (shopkeeper == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                orderRepository.findByShopOwnerId(
                        shopkeeper.getId()
                )
        );
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getMyOrder(
            @PathVariable Long orderId) {

        User shopkeeper = getCurrentShopkeeper();

        if (shopkeeper == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Order> orderOptional =
                orderRepository.findById(orderId);

        if (orderOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOptional.get();

        if (!isOwner(order, shopkeeper)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(order);
    }

    @PutMapping("/{orderId}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status) {

        User shopkeeper = getCurrentShopkeeper();

        if (shopkeeper == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Order> orderOptional =
                orderRepository.findById(orderId);

        if (orderOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOptional.get();

        if (!isOwner(order, shopkeeper)) {
            return ResponseEntity.status(403).build();
        }

        Optional<Order> updatedOrder =
                orderService.updateShopkeeperOrderStatus(
                        orderId,
                        status,
                        shopkeeper.getEmail()
                );

        return updatedOrder
                .map(ResponseEntity::ok)
                .orElse(
                        ResponseEntity.badRequest().build()
                );
    }

    @PutMapping("/{orderId}/reject")
    public ResponseEntity<Order> rejectOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {

        User shopkeeper = getCurrentShopkeeper();
        if (shopkeeper == null) {
            return ResponseEntity.status(401).build();
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isOwner(order, shopkeeper)) {
            return ResponseEntity.status(403).build();
        }
        if (order.getStatus() != OrderStatus.PLACED) {
            return ResponseEntity.badRequest().build();
        }

        String reason = request == null || request.getReason() == null
                || request.getReason().isBlank()
                ? "Rejected by shopkeeper"
                : request.getReason().trim();

        Optional<Order> rejectedOrder = orderService.rejectOrderByShopkeeper(
                orderId,
                shopkeeper.getEmail(),
                reason
        );

        return rejectedOrder
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancelOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {

        User shopkeeper = getCurrentShopkeeper();

        if (shopkeeper == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Order> orderOptional =
                orderRepository.findById(orderId);

        if (orderOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOptional.get();

        if (!isOwner(order, shopkeeper)) {
            return ResponseEntity.status(403).build();
        }

        Optional<Order> cancelledOrder =
                orderService.cancelOrderByShopkeeper(
                        orderId,
                        shopkeeper.getEmail(),
                        request == null || request.getReason() == null || request.getReason().isBlank()
                                ? "Cancelled by shopkeeper"
                                : request.getReason().trim()
                );

        return cancelledOrder
                .map(ResponseEntity::ok)
                .orElse(
                        ResponseEntity.badRequest().build()
                );
    }

    private User getCurrentShopkeeper() {

        String email =
                SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return null;
        }

        User user =
                userRepository.findByEmail(email)
                        .orElse(null);

        if (user == null) {
            return null;
        }

        if (user.getRole() != Role.SHOPKEEPER) {
            return null;
        }

        if (!user.isActive()) {
            return null;
        }

        return user;
    }

    private boolean isOwner(
            Order order,
            User shopkeeper) {

        return order.getShop() != null
                && order.getShop().getOwner() != null
                && order.getShop()
                .getOwner()
                .getId()
                .equals(shopkeeper.getId());
    }
}