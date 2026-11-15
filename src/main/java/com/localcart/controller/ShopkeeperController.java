package com.localcart.controller;

import com.localcart.dto.CreateShopRequest;
import com.localcart.dto.ShopResponse;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import com.localcart.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shopkeeper")
public class ShopkeeperController {

    private final ShopService shopService;
    private final UserRepository userRepository;

    public ShopkeeperController(
            ShopService shopService,
            UserRepository userRepository) {
        this.shopService = shopService;
        this.userRepository = userRepository;
    }

    @GetMapping("/shops")
    public ResponseEntity<List<ShopResponse>> getMyShops() {

        String email = SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (user.getRole() != Role.SHOPKEEPER) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(
                shopService.getShopsByOwner(user.getId())
        );
    }

    @PostMapping("/shops")
    public ResponseEntity<ShopResponse> createShop(
            @Valid @RequestBody CreateShopRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                shopService.createShop(email, request)
        );
    }
}