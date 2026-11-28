package com.localcart.dto;

import com.localcart.entity.DeliveryMode;

/**
 * Public shop details used by customer-facing marketplace endpoints.
 *
 * Owner names and phone numbers are intentionally not part of this response.
 */
public record CustomerMarketplaceShopResponse(
        Long id,
        String name,
        String address,
        boolean active,
        DeliveryMode deliveryMode,
        Double latitude,
        Double longitude,
        Double distanceKm
) {
}
