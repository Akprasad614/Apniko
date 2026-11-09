package com.localcart.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiInsightSummaryResponse(
        Long shopId,
        String shopName,
        LocalDateTime generatedAt,
        int historyDays,
        int productsAnalysed,
        int productsRequiringAttention,
        double estimatedDemandNextSevenDays,
        String forecastingMethod,
        List<ReplenishmentRecommendationResponse> recommendations
) {
}
