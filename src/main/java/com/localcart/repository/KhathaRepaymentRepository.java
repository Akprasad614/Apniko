package com.localcart.repository;

import com.localcart.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface KhathaRepaymentRepository extends JpaRepository<KhathaRepayment,Long> {
    Optional<KhathaRepayment> findByIdempotencyKey(String idempotencyKey);
    List<KhathaRepayment> findByAccountIdOrderByCreatedAtDesc(Long accountId);
    long countByStatus(KhathaRepaymentStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from KhathaRepayment r where r.id=:id")
    Optional<KhathaRepayment> findByIdForUpdate(@Param("id") Long id);
}
