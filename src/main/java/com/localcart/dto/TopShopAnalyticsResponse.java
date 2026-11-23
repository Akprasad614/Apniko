package com.localcart.dto;

import java.math.BigDecimal;

public record TopShopAnalyticsResponse(
        Long shopId,
        String shopName,
        long totalOrders,
        long deliveredOrders,
        long cancelledOrders,
        BigDecimal nonCancelledOrderValue
) {
}
