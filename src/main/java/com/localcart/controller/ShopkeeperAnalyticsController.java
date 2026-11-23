package com.localcart.controller;

import com.localcart.dto.AnalyticsOverviewResponse;
import com.localcart.dto.DailyAnalyticsResponse;
import com.localcart.dto.TopProductAnalyticsResponse;
import com.localcart.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shopkeeper/analytics")
@PreAuthorize("hasRole('SHOPKEEPER')")
public class ShopkeeperAnalyticsController {

    private final AnalyticsService analyticsService;

    public ShopkeeperAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsService.getShopkeeperOverview(from, to));
    }

    @GetMapping("/daily")
    public ResponseEntity<List<DailyAnalyticsResponse>> getDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsService.getShopkeeperDaily(from, to));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductAnalyticsResponse>> getTopProducts(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
                analyticsService.getShopkeeperTopProducts(shopId, from, to, limit)
        );
    }
}
