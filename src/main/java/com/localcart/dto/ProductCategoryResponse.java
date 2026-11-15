package com.localcart.dto;

import java.util.List;

public record ProductCategoryResponse(
        Long id,
        String name,
        String slug,
        List<ProductCategoryResponse> subcategories
) {
}
