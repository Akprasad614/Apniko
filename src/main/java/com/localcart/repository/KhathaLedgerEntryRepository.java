package com.localcart.repository;

import com.localcart.entity.KhathaLedgerEntry;
import com.localcart.entity.KhathaLedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface KhathaLedgerEntryRepository extends JpaRepository<KhathaLedgerEntry,Long> {
    boolean existsByAccountIdAndSourceTypeAndSourceId(Long accountId,String sourceType,String sourceId);
    Optional<KhathaLedgerEntry> findByAccountIdAndSourceTypeAndSourceId(Long accountId,String sourceType,String sourceId);
    List<KhathaLedgerEntry> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);
    List<KhathaLedgerEntry> findByAccountIdAndEntryTypeInOrderByCreatedAtAscIdAsc(Long accountId, java.util.Collection<KhathaLedgerEntryType> entryTypes);
}
