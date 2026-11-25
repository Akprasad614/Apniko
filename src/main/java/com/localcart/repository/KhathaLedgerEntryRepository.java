package com.localcart.repository;

import com.localcart.entity.KhathaLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface KhathaLedgerEntryRepository extends JpaRepository<KhathaLedgerEntry,Long> {
    boolean existsByAccountIdAndSourceTypeAndSourceId(Long accountId,String sourceType,String sourceId);
    Optional<KhathaLedgerEntry> findByAccountIdAndSourceTypeAndSourceId(Long accountId,String sourceType,String sourceId);
    List<KhathaLedgerEntry> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);
}
