package com.localcart.dto;

public record AdminAnalyticsOverviewResponse(
        AnalyticsOverviewResponse orderMetrics,
        long activeShops,
        long activeCustomerAccounts,
        long activeShopkeeperAccounts,
        long activeDeliveryPartnerAccounts
) {
}
