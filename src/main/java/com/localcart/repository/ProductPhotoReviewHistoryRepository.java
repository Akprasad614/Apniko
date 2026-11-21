package com.localcart.repository;

import com.localcart.entity.ProductPhotoReviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPhotoReviewHistoryRepository
        extends JpaRepository<ProductPhotoReviewHistory, Long> {
    long countByOutcome(String outcome);
}
