package com.localcart.dto;

import java.math.BigDecimal;

public record TopProductAnalyticsResponse(
        Long shopId,
        String shopName,
        Long productId,
        String productName,
        long unitsSold,
        BigDecimal orderValue
) {
}
