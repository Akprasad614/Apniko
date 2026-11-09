package com.localcart.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyAnalyticsResponse(
        LocalDate date,
        long totalOrders,
        long deliveredOrders,
        long cancelledOrders,
        BigDecimal nonCancelledOrderValue
) {
}
