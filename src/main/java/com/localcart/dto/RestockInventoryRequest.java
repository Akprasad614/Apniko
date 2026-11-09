package com.localcart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RestockInventoryRequest {

    @NotNull
    @Min(value = 1, message = "Restock quantity must be at least 1")
    private Integer quantity;

    @Size(max = 500, message = "Note cannot exceed 500 characters")
    private String note;

    public RestockInventoryRequest() {
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
