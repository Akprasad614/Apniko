package com.localcart.dto;

import com.localcart.entity.DeliveryMode;
import jakarta.validation.constraints.NotNull;

public class UpdateShopDeliveryModeRequest {

    @NotNull
    private DeliveryMode deliveryMode;

    public UpdateShopDeliveryModeRequest() {
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }
}
