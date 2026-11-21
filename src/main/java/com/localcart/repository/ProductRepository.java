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
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByBarcode(String barcode);
    Optional<Product> findByIdentityKey(String identityKey);

    @Query("""
            SELECT product
            FROM Product product
            LEFT JOIN FETCH product.photo
            WHERE product.catalogueReviewStatus IS NULL
               OR product.catalogueReviewStatus = :visibleStatus
            """)
    List<Product> findAllVisibleWithPhoto(
            @Param("visibleStatus") ProductCatalogueReviewStatus visibleStatus
    );
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

    @Query(value = """
            SELECT product
            FROM Product product
            LEFT JOIN FETCH product.photo
            WHERE (product.catalogueReviewStatus IS NULL
                   OR product.catalogueReviewStatus = :visibleStatus)
              AND NOT EXISTS (
                  SELECT inventory.id
                  FROM ShopInventory inventory
                  WHERE inventory.shop.id = :shopId
                    AND inventory.product.id = product.id
              )
              AND (:searchTerm IS NULL
                   OR LOWER(product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR COALESCE(product.barcode, '') LIKE CONCAT('%', :searchTerm, '%')
                   OR LOWER(COALESCE(product.category, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(product.subcategory, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(product.packSize, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """,
            countQuery = """
            SELECT COUNT(product)
            FROM Product product
            WHERE (product.catalogueReviewStatus IS NULL
                   OR product.catalogueReviewStatus = :visibleStatus)
              AND NOT EXISTS (
                  SELECT inventory.id
                  FROM ShopInventory inventory
                  WHERE inventory.shop.id = :shopId
                    AND inventory.product.id = product.id
              )
              AND (:searchTerm IS NULL
                   OR LOWER(product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(product.brand, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR COALESCE(product.barcode, '') LIKE CONCAT('%', :searchTerm, '%')
                   OR LOWER(COALESCE(product.category, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(product.subcategory, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(product.packSize, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<Product> findAddableProductsForShop(
            @Param("shopId") Long shopId,
            @Param("searchTerm") String searchTerm,
            @Param("visibleStatus") ProductCatalogueReviewStatus visibleStatus,
            Pageable pageable
    );

    @Query("""
            SELECT product
            FROM Product product
            LEFT JOIN product.photo photo
            WHERE product.barcode IS NOT NULL
              AND (photo IS NULL OR photo.approvedImageUrl IS NULL)
              AND (photo IS NULL OR photo.reviewStatus IS NULL
                   OR photo.reviewStatus = :rejectedStatus)
            """)
    Page<Product> findProductsMissingApprovedPhoto(
            @Param("rejectedStatus") ProductImageReviewStatus rejectedStatus,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(product)
            FROM Product product
            WHERE product.barcode IS NOT NULL
              AND TRIM(product.barcode) <> ''
            """)
    long countProductsWithBarcode();

    @Query("""
            SELECT COUNT(product)
            FROM Product product
            LEFT JOIN product.photo photo
            WHERE product.barcode IS NOT NULL
              AND TRIM(product.barcode) <> ''
              AND (photo IS NULL OR photo.approvedImageUrl IS NULL)
              AND (photo IS NULL OR photo.reviewStatus IS NULL
                   OR photo.reviewStatus = :rejectedStatus)
            """)
    long countProductsWithBarcodeMissingApprovedPhoto(
            @Param("rejectedStatus") ProductImageReviewStatus rejectedStatus
    );

}
