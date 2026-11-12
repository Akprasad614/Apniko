package com.localcart.dto;

import com.localcart.entity.DeliveryMode;
import jakarta.validation.constraints.NotNull;

public class CheckoutRequest {

    @NotNull
    private DeliveryMode deliveryMode;

    @jakarta.validation.constraints.Positive
    private Long addressId;

    public CheckoutRequest() {
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }
}