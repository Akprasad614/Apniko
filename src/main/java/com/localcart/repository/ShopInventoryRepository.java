package com.localcart.repository;

import com.localcart.entity.ShopInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopInventoryRepository
        extends JpaRepository<ShopInventory, Long> {

    Optional<ShopInventory> findByShopIdAndProductId(
            Long shopId,
            Long productId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT inventory
            FROM ShopInventory inventory
            WHERE inventory.shop.id = :shopId
            AND inventory.product.id = :productId
            """)
    Optional<ShopInventory> findByShopIdAndProductIdForUpdate(
            @Param("shopId") Long shopId,
            @Param("productId") Long productId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT inventory
            FROM ShopInventory inventory
            WHERE inventory.id = :inventoryId
            """)
    Optional<ShopInventory> findByIdForUpdate(
            @Param("inventoryId") Long inventoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT inventory
            FROM ShopInventory inventory
            WHERE inventory.id = :inventoryId
            AND inventory.shop.id = :shopId
            """)
    Optional<ShopInventory> findByIdAndShopIdForUpdate(
            @Param("inventoryId") Long inventoryId,
            @Param("shopId") Long shopId
    );

    List<ShopInventory> findByShopId(Long shopId);

    /**
     * Searches only products that customers can currently buy:
     * the shop is active, inventory is enabled, and stock is positive.
     */
    @Query(value = """
            SELECT inventory
            FROM ShopInventory inventory
            JOIN inventory.shop shop
            JOIN inventory.product product
            WHERE shop.active = true
              AND inventory.available = true
              AND inventory.stockQuantity > 0
              AND (:searchTerm IS NULL OR
                   LOWER(product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
              AND (:category IS NULL OR
                   LOWER(product.category) = LOWER(:category))
              AND (:shopId IS NULL OR shop.id = :shopId)
              AND (:minPrice IS NULL OR inventory.price >= :minPrice)
              AND (:maxPrice IS NULL OR inventory.price <= :maxPrice)
            """,
            countQuery = """
            SELECT COUNT(inventory)
            FROM ShopInventory inventory
            JOIN inventory.shop shop
            JOIN inventory.product product
            WHERE shop.active = true
              AND inventory.available = true
              AND inventory.stockQuantity > 0
              AND (:searchTerm IS NULL OR
                   LOWER(product.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
              AND (:category IS NULL OR
                   LOWER(product.category) = LOWER(:category))
              AND (:shopId IS NULL OR shop.id = :shopId)
              AND (:minPrice IS NULL OR inventory.price >= :minPrice)
              AND (:maxPrice IS NULL OR inventory.price <= :maxPrice)
            """)
    Page<ShopInventory> searchMarketplace(
            @Param("searchTerm") String searchTerm,
            @Param("category") String category,
            @Param("shopId") Long shopId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable
    );

    @Query("""
            SELECT inventory
            FROM ShopInventory inventory
            JOIN inventory.shop shop
            WHERE inventory.product.id = :productId
              AND shop.active = true
              AND inventory.available = true
              AND inventory.stockQuantity > 0
            ORDER BY inventory.price ASC, shop.name ASC
            """)
    List<ShopInventory> findAvailablePricesForProduct(
            @Param("productId") Long productId
    );

    @Query("""
            SELECT DISTINCT product.category
            FROM ShopInventory inventory
            JOIN inventory.shop shop
            JOIN inventory.product product
            WHERE shop.active = true
              AND inventory.available = true
              AND inventory.stockQuantity > 0
              AND product.category IS NOT NULL
              AND TRIM(product.category) <> ''
            ORDER BY product.category ASC
            """)
    List<String> findAvailableMarketplaceCategories();
}
