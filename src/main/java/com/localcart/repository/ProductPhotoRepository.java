package com.localcart.repository;

import com.localcart.entity.ProductPhoto;
import com.localcart.entity.ProductImageReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, Long> {
    Optional<ProductPhoto> findByProductId(Long productId);
    Page<ProductPhoto> findByReviewStatus(ProductImageReviewStatus status, Pageable pageable);
    long countByReviewStatus(ProductImageReviewStatus status);
    long countByReviewStatusAndLicense(ProductImageReviewStatus status, String license);
    long countByReviewStatusAndApprovedImageUrlIsNotNull(ProductImageReviewStatus status);


}
