package com.localcart.dto;

import com.localcart.entity.DeliveryMode;

public class CheckoutRequest {

    // Optional for compatibility with older clients. The shop's saved
    // fulfilment setting is authoritative during checkout.
    private DeliveryMode deliveryMode;

    private Long addressId;

    public CheckoutRequest() {
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }
}