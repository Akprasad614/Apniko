package com.localcart.repository;

import com.localcart.entity.KhathaBalanceAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KhathaBalanceAllocationRepository extends JpaRepository<KhathaBalanceAllocation,Long> {
    List<KhathaBalanceAllocation> findByDebitEntryIdOrderByCreatedAtAscIdAsc(Long debitEntryId);
}
