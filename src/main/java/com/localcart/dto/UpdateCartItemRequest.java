package com.localcart.dto;

import jakarta.validation.constraints.Min;

public class UpdateCartItemRequest {

    @Min(
            value = 1,
            message = "Quantity must be at least 1"
    )
    private int quantity;

    public UpdateCartItemRequest() {
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}