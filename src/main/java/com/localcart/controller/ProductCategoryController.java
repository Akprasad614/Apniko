package com.localcart.controller;

import com.localcart.dto.CreateProductCategoryRequest;
import com.localcart.dto.ProductCategoryResponse;
import com.localcart.service.ProductCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogue/categories")
public class ProductCategoryController {

    private final ProductCategoryService service;

    public ProductCategoryController(ProductCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ProductCategoryResponse>> getCategoryTree() {
        return ResponseEntity.ok(service.getCategoryTree());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductCategoryResponse> createCategory(
            @Valid @RequestBody CreateProductCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createCategory(request));
    }
}
