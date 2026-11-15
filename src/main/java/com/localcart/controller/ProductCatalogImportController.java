package com.localcart.controller;

import com.localcart.dto.ProductImportBatchRequest;
import com.localcart.dto.ProductImportBatchResponse;
import com.localcart.service.ProductCatalogImportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/catalogue")
@PreAuthorize("hasRole('ADMIN')")
public class ProductCatalogImportController {

    private final ProductCatalogImportService importService;

    public ProductCatalogImportController(ProductCatalogImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/import")
    public ResponseEntity<ProductImportBatchResponse> importBatch(
            @Valid @RequestBody ProductImportBatchRequest request) {
        return ResponseEntity.ok(importService.importBatch(request));
    }
}
