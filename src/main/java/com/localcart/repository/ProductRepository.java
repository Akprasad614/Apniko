package com.localcart.repository;

import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueReviewStatus;
import com.localcart.entity.ProductImageReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByBarcode(String barcode);
    Optional<Product> findByIdentityKey(String identityKey);
    @Query("""
            SELECT product
            FROM Product product
            WHERE (:status IS NULL OR product.catalogueReviewStatus = :status)
              AND (:searchTerm IS NULL
                   OR LOWER(product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR COALESCE(product.barcode, '') LIKE CONCAT('%', :searchTerm, '%')
                   OR LOWER(COALESCE(product.category, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(product.subcategory, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<Product> searchCatalogueReviewQueue(
            @Param("status") ProductCatalogueReviewStatus status,
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );
}
