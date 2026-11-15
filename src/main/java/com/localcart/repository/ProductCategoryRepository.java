package com.localcart.repository;

import com.localcart.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    Optional<ProductCategory> findBySlug(String slug);
    Optional<ProductCategory> findByNameIgnoreCaseAndParentIsNull(String name);
    Optional<ProductCategory> findByNameIgnoreCaseAndParent_Id(String name, Long parentId);
    List<ProductCategory> findByParentIsNullAndActiveTrueOrderByNameAsc();
    List<ProductCategory> findByParent_IdAndActiveTrueOrderByNameAsc(Long parentId);
    boolean existsBySlug(String slug);
}
