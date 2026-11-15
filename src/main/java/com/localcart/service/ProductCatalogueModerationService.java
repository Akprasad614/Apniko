package com.localcart.service;

import com.localcart.dto.CatalogueReviewItemResponse;
import com.localcart.dto.CatalogueReviewUpdateRequest;
import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueReviewStatus;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@Transactional
public class ProductCatalogueModerationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;

    public ProductCatalogueModerationService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<CatalogueReviewItemResponse> getReviewQueue(
            String requestedStatus, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page number cannot be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "id"));
        String normalizedStatus = requestedStatus == null || requestedStatus.isBlank()
                ? ProductCatalogueReviewStatus.NEEDS_REVIEW.name()
                : requestedStatus.trim().toUpperCase(Locale.ROOT);

        Page<Product> products;
        if ("ALL".equals(normalizedStatus)) {
            products = productRepository.findAll(pageable);
        } else {
            ProductCatalogueReviewStatus status;
            try {
                status = ProductCatalogueReviewStatus.valueOf(normalizedStatus);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException(
                        "Status must be NEEDS_REVIEW, ACTIVE, HIDDEN or ALL");
            }
            products = productRepository.findByCatalogueReviewStatus(status, pageable);
        }
        return products.map(this::toResponse);
    }

    public CatalogueReviewItemResponse updateReview(
            Long productId, CatalogueReviewUpdateRequest request, String adminEmail) {
        if (request == null || request.status() == null) {
            throw new BadRequestException("Catalogue review status is required");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        String reason = request.reason() == null ? null : request.reason().trim();
        if (request.status() == ProductCatalogueReviewStatus.HIDDEN
                && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A reason is required when hiding a product");
        }

        product.setCatalogueReviewStatus(request.status());
        if (request.status() == ProductCatalogueReviewStatus.NEEDS_REVIEW) {
            product.setCatalogueReviewReason(
                    reason == null || reason.isBlank()
                            ? "Returned to the catalogue review queue" : reason);
            product.setCatalogueReviewedAt(null);
            product.setCatalogueReviewedBy(null);
        } else {
            product.setCatalogueReviewReason(
                    request.status() == ProductCatalogueReviewStatus.HIDDEN ? reason : null);
            product.setCatalogueReviewedAt(Instant.now());
            product.setCatalogueReviewedBy(
                    adminEmail == null || adminEmail.isBlank() ? "admin" : adminEmail.trim());
        }

        return toResponse(productRepository.save(product));
    }

    private CatalogueReviewItemResponse toResponse(Product product) {
        return new CatalogueReviewItemResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getBarcode(),
                product.getPackSize(),
                product.getCategory(),
                product.getSubcategory(),
                product.getCatalogueSource(),
                product.getCatalogueReviewStatus(),
                product.getCatalogueReviewReason(),
                product.getCatalogueImportedAt(),
                product.getCatalogueReviewedAt(),
                product.getCatalogueReviewedBy()
        );
    }
}
