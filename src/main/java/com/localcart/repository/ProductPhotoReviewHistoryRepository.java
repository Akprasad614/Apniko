package com.localcart.repository;

import com.localcart.entity.ProductPhotoReviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;

public interface ProductPhotoReviewHistoryRepository
        extends JpaRepository<ProductPhotoReviewHistory, Long> {
    long countByOutcome(String outcome);
    boolean existsByProductIdAndCandidateImageUrlAndOutcomeIn(
            Long productId, String candidateImageUrl, Collection<String> outcomes);
}
