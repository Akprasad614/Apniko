package com.localcart.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DemandForecastResponse(
        Long shopId,
        String shopName,
        Long productId,
        String productName,
        LocalDate historyFrom,
        LocalDate historyTo,
        int historyDays,
        int activeSalesDays,
        int forecastDays,
        double averageDailyUnits,
        double predictedDailyUnits,
        double predictedUnitsForPeriod,
        String confidence,
        String model,
        LocalDateTime generatedAt,
        List<DailyDemandForecastResponse> dailyForecast
) {
}
