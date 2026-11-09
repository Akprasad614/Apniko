package com.localcart.controller;

import com.localcart.dto.AiInsightSummaryResponse;
import com.localcart.dto.DemandForecastResponse;
import com.localcart.dto.ReplenishmentRecommendationResponse;
import com.localcart.service.ShopkeeperAiInsightsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shopkeeper/ai-insights")
@PreAuthorize("hasRole('SHOPKEEPER')")
public class ShopkeeperAiInsightsController {

    private final ShopkeeperAiInsightsService insightsService;

    public ShopkeeperAiInsightsController(ShopkeeperAiInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/forecast")
    public ResponseEntity<DemandForecastResponse> getProductForecast(
            @RequestParam Long shopId,
            @RequestParam Long productId,
            @RequestParam(required = false, defaultValue = "7") Integer forecastDays,
            @RequestParam(required = false, defaultValue = "28") Integer historyDays) {
        return ResponseEntity.ok(insightsService.getProductForecast(
                shopId, productId, forecastDays, historyDays));
    }

    @GetMapping("/replenishment")
    public ResponseEntity<List<ReplenishmentRecommendationResponse>> getReplenishmentRecommendations(
            @RequestParam Long shopId,
            @RequestParam(required = false, defaultValue = "28") Integer historyDays,
            @RequestParam(required = false, defaultValue = "3") Integer leadTimeDays) {
        return ResponseEntity.ok(insightsService.getReplenishmentRecommendations(
                shopId, historyDays, leadTimeDays));
    }

    @GetMapping("/summary")
    public ResponseEntity<AiInsightSummaryResponse> getSummary(
            @RequestParam Long shopId,
            @RequestParam(required = false, defaultValue = "28") Integer historyDays) {
        return ResponseEntity.ok(insightsService.getSummary(shopId, historyDays));
    }
}
