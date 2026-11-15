package com.localcart.repository;

import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByBarcode(String barcode);
    Optional<Product> findByIdentityKey(String identityKey);
    Page<Product> findByCatalogueReviewStatus(ProductCatalogueReviewStatus status, Pageable pageable);
}
