package com.localcart.controller;

import com.localcart.dto.AddInventoryRequest;
import com.localcart.dto.InventoryMovementResponse;
import com.localcart.dto.InventoryResponse;
import com.localcart.dto.RestockInventoryRequest;
import com.localcart.dto.UpdateInventoryRequest;
import com.localcart.dto.UpdateLowStockThresholdRequest;
import com.localcart.security.SecurityUtils;
import com.localcart.service.ShopInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shopkeeper/shops")
public class ShopInventoryController {

    private final ShopInventoryService inventoryService;

    public ShopInventoryController(ShopInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/{shopId}/inventory")
    public ResponseEntity<InventoryResponse> addInventory(
            @PathVariable Long shopId,
            @Valid @RequestBody AddInventoryRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(inventoryService.addInventory(email, shopId, request));
    }

    @GetMapping("/{shopId}/inventory")
    public ResponseEntity<List<InventoryResponse>> getMyInventory(
            @PathVariable Long shopId) {

        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(inventoryService.getMyInventory(email, shopId));
    }

    @PutMapping("/inventory/{inventoryId}")
    public ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable Long inventoryId,
            @Valid @RequestBody UpdateInventoryRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(inventoryService.updateInventory(email, inventoryId, request));
    }

    @PostMapping("/{shopId}/inventory/{inventoryId}/restock")
    public ResponseEntity<InventoryResponse> restock(
            @PathVariable Long shopId,
            @PathVariable Long inventoryId,
            @Valid @RequestBody RestockInventoryRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(
                inventoryService.restock(email, shopId, inventoryId, request)
        );
    }

    @PutMapping("/{shopId}/inventory/{inventoryId}/low-stock-threshold")
    public ResponseEntity<InventoryResponse> updateLowStockThreshold(
            @PathVariable Long shopId,
            @PathVariable Long inventoryId,
            @Valid @RequestBody UpdateLowStockThresholdRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(
                inventoryService.updateLowStockThreshold(
                        email, shopId, inventoryId, request
                )
        );
    }

    @GetMapping("/{shopId}/inventory/low-stock")
    public ResponseEntity<List<InventoryResponse>> getLowStockItems(
            @PathVariable Long shopId) {

        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(inventoryService.getLowStockItems(email, shopId));
    }

    @GetMapping("/{shopId}/inventory/{inventoryId}/movements")
    public ResponseEntity<List<InventoryMovementResponse>> getMovementHistory(
            @PathVariable Long shopId,
            @PathVariable Long inventoryId) {

        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(
                inventoryService.getMovementHistory(email, shopId, inventoryId)
        );
    }
}
