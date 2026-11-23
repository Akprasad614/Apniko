package com.localcart.dto;

import jakarta.validation.constraints.Min;

public class UpdateInventoryRequest {

    @Min(0)
    private double price;

    @Min(0)
    private int stockQuantity;

    private boolean available;

    public UpdateInventoryRequest() {
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}