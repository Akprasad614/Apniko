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

    @org.springframework.data.jpa.repository.Query("""
            select count(photo)
            from ProductPhoto photo
            where photo.reviewStatus = :status
              and photo.sourceName in (
                'Open Food Facts', 'Open Beauty Facts',
                'Open Pet Food Facts', 'Open Products Facts')
            """)
    long countApprovedOpenFactsPhotos(
            @org.springframework.data.repository.query.Param("status")
            ProductImageReviewStatus status);
}
