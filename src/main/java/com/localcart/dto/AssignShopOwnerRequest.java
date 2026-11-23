package com.localcart.dto;

import jakarta.validation.constraints.NotNull;

public class AssignShopOwnerRequest {

    @NotNull(message = "ownerId is required")
    private Long ownerId;

    public AssignShopOwnerRequest() {
    }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
}
