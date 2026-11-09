package com.localcart.controller;

import com.localcart.dto.InventoryResponse;
import com.localcart.dto.PriceComparisonResponse;
import com.localcart.dto.ShopResponse;
import com.localcart.service.CustomerMarketplaceService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/marketplace")
public class CustomerMarketplaceController {

    private final CustomerMarketplaceService marketplaceService;

    public CustomerMarketplaceController(
            CustomerMarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    @GetMapping("/shops")
    public ResponseEntity<List<ShopResponse>> getActiveShops() {
        return ResponseEntity.ok(marketplaceService.getActiveShops());
    }

    @GetMapping("/shops/{shopId}")
    public ResponseEntity<ShopResponse> getActiveShop(
            @PathVariable Long shopId) {
        return ResponseEntity.ok(marketplaceService.getActiveShop(shopId));
    }

    @GetMapping("/shops/{shopId}/products")
    public ResponseEntity<List<InventoryResponse>> getShopProducts(
            @PathVariable Long shopId) {
        return ResponseEntity.ok(
                marketplaceService.getShopProducts(shopId));
    }

    @GetMapping("/products/search")
    public ResponseEntity<Page<InventoryResponse>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        return ResponseEntity.ok(
                marketplaceService.searchProducts(
                        q,
                        category,
                        shopId,
                        minPrice,
                        maxPrice,
                        page,
                        size,
                        sortBy,
                        direction
                )
        );
    }

    @GetMapping("/products/{productId}/prices")
    public ResponseEntity<List<PriceComparisonResponse>> compareProductPrices(
            @PathVariable Long productId) {
        return ResponseEntity.ok(
                marketplaceService.compareProductPrices(productId));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAvailableCategories() {
        return ResponseEntity.ok(
                marketplaceService.getAvailableCategories());
    }
}
