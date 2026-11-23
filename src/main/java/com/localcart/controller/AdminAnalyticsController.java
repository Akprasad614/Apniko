package com.localcart.controller;

import com.localcart.dto.AdminAnalyticsOverviewResponse;
import com.localcart.dto.DailyAnalyticsResponse;
import com.localcart.dto.TopProductAnalyticsResponse;
import com.localcart.dto.TopShopAnalyticsResponse;
import com.localcart.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<AdminAnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsService.getAdminOverview(from, to));
    }

    @GetMapping("/daily")
    public ResponseEntity<List<DailyAnalyticsResponse>> getDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsService.getAdminDaily(from, to));
    }

    @GetMapping("/top-shops")
    public ResponseEntity<List<TopShopAnalyticsResponse>> getTopShops(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getAdminTopShops(from, to, limit));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductAnalyticsResponse>> getTopProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getAdminTopProducts(from, to, limit));
    }
}
