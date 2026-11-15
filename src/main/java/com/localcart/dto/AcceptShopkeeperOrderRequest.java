package com.localcart.dto;

import com.localcart.entity.DeliveryMode;
import jakarta.validation.constraints.NotNull;

public class AcceptShopkeeperOrderRequest {

    @NotNull
    private DeliveryMode deliveryMode;

    public AcceptShopkeeperOrderRequest() {
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }
}
