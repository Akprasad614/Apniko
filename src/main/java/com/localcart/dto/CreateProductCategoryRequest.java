package com.localcart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        Long parentCategoryId
) {
}
