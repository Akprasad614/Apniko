package com.localcart.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnalyticsOverviewResponse(
        LocalDate from,
        LocalDate to,
        long totalOrders,
        long deliveredOrders,
        long cancelledOrders,
        long openOrders,
        long customersWithOrders,
        long unitsInNonCancelledOrders,
        BigDecimal grossOrderValue,
        BigDecimal nonCancelledOrderValue,
        BigDecimal deliveredOrderValue,
        BigDecimal averageNonCancelledOrderValue,
        long shopsCovered
) {
}
