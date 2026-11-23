package com.localcart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateLowStockThresholdRequest {

    @NotNull
    @Min(value = 0, message = "Low-stock threshold cannot be negative")
    private Integer lowStockThreshold;

    public UpdateLowStockThresholdRequest() {
    }

    public Integer getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(Integer lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
