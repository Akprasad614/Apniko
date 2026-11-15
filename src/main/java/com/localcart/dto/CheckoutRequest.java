package com.localcart.dto;

public class CheckoutRequest {

    private Long addressId;

    public CheckoutRequest() {
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }
}
