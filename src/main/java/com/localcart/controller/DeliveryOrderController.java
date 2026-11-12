package com.localcart.controller;

import com.localcart.entity.Order;
import com.localcart.security.SecurityUtils;
import com.localcart.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delivery/orders")
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
public class DeliveryOrderController {

    private final OrderService orderService;

    public DeliveryOrderController(
            OrderService orderService) {

        this.orderService = orderService;
    }

    // Orders that are READY and need platform delivery
    @GetMapping("/available")
    public ResponseEntity<List<Order>> getAvailableOrders() {

        String email =
                SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                orderService.getAvailableDeliveryOrders()
        );
    }

    // Orders assigned to the current delivery partner
    @GetMapping
    public ResponseEntity<List<Order>> getMyOrders() {

        String email =
                SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                orderService.getMyDeliveryOrders(email)
        );
    }

    // Accept a READY platform-delivery order
    @PutMapping("/{orderId}/accept")
    public ResponseEntity<Order> acceptOrder(
            @PathVariable Long orderId) {

        String email =
                SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        try {

            Order order =
                    orderService.acceptDeliveryOrder(
                            orderId,
                            email
                    );

            return ResponseEntity.ok(order);

        } catch (RuntimeException exception) {

            return ResponseEntity.badRequest().build();
        }
    }

    // Pick up the order from the shop
    @PutMapping("/{orderId}/pickup")
    public ResponseEntity<Order> pickupOrder(
            @PathVariable Long orderId) {

        String email =
                SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        try {

            Order order =
                    orderService.pickupOrder(
                            orderId,
                            email
                    );

            return ResponseEntity.ok(order);

        } catch (RuntimeException exception) {

            return ResponseEntity.badRequest().build();
        }
    }

    // Complete the delivery
    @PutMapping("/{orderId}/deliver")
    public ResponseEntity<Order> deliverOrder(
            @PathVariable Long orderId) {

        String email =
                SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        try {

            Order order =
                    orderService.deliverOrder(
                            orderId,
                            email
                    );

            return ResponseEntity.ok(order);

        } catch (RuntimeException exception) {

            return ResponseEntity.badRequest().build();
        }
    }
}
