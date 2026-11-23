package com.localcart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class AddToCartRequest {

    @NotNull
    private Long productId;

    @Min(
            value = 1,
            message = "Quantity must be at least 1"
    )
    private int quantity;

    public AddToCartRequest() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}