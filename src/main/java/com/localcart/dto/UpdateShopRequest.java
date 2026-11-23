package com.localcart.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateShopRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    @NotBlank
    private String address;

    public UpdateShopRequest() {
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }
}