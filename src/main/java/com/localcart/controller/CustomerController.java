package com.localcart.controller;

import com.localcart.entity.Order;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import com.localcart.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    public CustomerController(
            OrderService orderService,
            UserRepository userRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<Order>> getMyOrders() {

        String email = SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).filter(User::isActive).orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (user.getRole() != Role.CUSTOMER) {
            return ResponseEntity.status(403).build();
        }

        return orderService.getOrdersByCustomer(user.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}