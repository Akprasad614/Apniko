package com.localcart.dto;

import jakarta.validation.constraints.Size;

public class ProductImageRequest {

    @Size(max = 2048)
    private String imageUrl;

    public ProductImageRequest() {
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
