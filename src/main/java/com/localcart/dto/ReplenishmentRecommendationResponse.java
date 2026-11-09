package com.localcart.dto;

public record ReplenishmentRecommendationResponse(
        Long shopId,
        String shopName,
        Long inventoryId,
        Long productId,
        String productName,
        int currentStock,
        int lowStockThreshold,
        double predictedDailyDemand,
        int leadTimeDays,
        int expectedDemandDuringLeadTime,
        int safetyStock,
        int suggestedReorderQuantity,
        Double estimatedDaysOfCover,
        String status,
        String explanation
) {
}
