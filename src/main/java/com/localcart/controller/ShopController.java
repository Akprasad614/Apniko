package com.localcart.controller;

import com.localcart.dto.CreateShopRequest;
import com.localcart.dto.ShopResponse;
import com.localcart.dto.UpdateShopRequest;
import com.localcart.security.SecurityUtils;
import com.localcart.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shops")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @PostMapping
    public ResponseEntity<ShopResponse> createShop(
            @Valid @RequestBody CreateShopRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();

        return ResponseEntity.ok(
                shopService.createShop(
                        email,
                        request
                )
        );
    }

    @GetMapping
    public ResponseEntity<List<ShopResponse>> getAllShops() {
        return ResponseEntity.ok(shopService.getAllShops());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShopResponse> getShopById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                shopService.getShopById(id)
        );
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<ShopResponse>> getShopsByOwner(
            @PathVariable Long ownerId) {

        return ResponseEntity.ok(
                shopService.getShopsByOwner(ownerId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShopResponse> updateShop(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShopRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();

        return ResponseEntity.ok(
                shopService.updateShop(
                        id,
                        email,
                        request
                )
        );
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ShopResponse> deactivateShop(
            @PathVariable Long id) {

        String email = SecurityUtils.getCurrentUserEmail();

        return ResponseEntity.ok(
                shopService.deactivateShop(
                        id,
                        email
                )
        );
    }
}