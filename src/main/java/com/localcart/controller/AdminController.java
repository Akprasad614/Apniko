package com.localcart.controller;

import com.localcart.dto.AdminShopResponse;
import com.localcart.dto.AssignShopOwnerRequest;
import com.localcart.dto.CreateManagedUserRequest;
import com.localcart.dto.UpdateActiveStatusRequest;
import com.localcart.dto.UserResponse;
import com.localcart.entity.Role;
import com.localcart.security.SecurityUtils;
import com.localcart.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/users/shopkeepers")
    public ResponseEntity<UserResponse> createShopkeeper(
            @Valid @RequestBody CreateManagedUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createShopkeeper(request));
    }

    @PostMapping("/users/delivery-partners")
    public ResponseEntity<UserResponse> createDeliveryPartner(
            @Valid @RequestBody CreateManagedUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createDeliveryPartner(request));
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers(
            @RequestParam(required = false) Role role) {
        return ResponseEntity.ok(adminService.getUsers(role));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.getUser(userId));
    }

    @PatchMapping("/users/{userId}/active")
    public ResponseEntity<UserResponse> updateUserActiveStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ResponseEntity.ok(adminService.updateUserActiveStatus(
                userId, request, SecurityUtils.getCurrentUserEmail()
        ));
    }

    @GetMapping("/shops")
    public ResponseEntity<List<AdminShopResponse>> getShops() {
        return ResponseEntity.ok(adminService.getShops());
    }

    @PutMapping("/shops/{shopId}/owner")
    public ResponseEntity<AdminShopResponse> assignShopOwner(
            @PathVariable Long shopId,
            @Valid @RequestBody AssignShopOwnerRequest request) {
        return ResponseEntity.ok(adminService.assignShopOwner(shopId, request));
    }

    @PatchMapping("/shops/{shopId}/active")
    public ResponseEntity<AdminShopResponse> updateShopActiveStatus(
            @PathVariable Long shopId,
            @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ResponseEntity.ok(adminService.updateShopActiveStatus(shopId, request));
    }
}
