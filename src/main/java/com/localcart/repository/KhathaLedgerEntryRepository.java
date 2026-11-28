package com.localcart.repository;

import com.localcart.entity.KhathaLedgerEntry;
import com.localcart.entity.KhathaLedgerEntryType;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Deliberately exposes only append and read operations.
 * Do not extend JpaRepository here: its inherited delete methods would make
 * the financial ledger mutable through the application repository.
 */
public interface KhathaLedgerEntryRepository extends Repository<KhathaLedgerEntry, Long> {
    <S extends KhathaLedgerEntry> S save(S entry);

    boolean existsByAccountIdAndSourceTypeAndSourceId(
            Long accountId, String sourceType, String sourceId);

    Optional<KhathaLedgerEntry> findByAccountIdAndSourceTypeAndSourceId(
            Long accountId, String sourceType, String sourceId);

    List<KhathaLedgerEntry> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    List<KhathaLedgerEntry> findByAccountIdAndEntryTypeInOrderByCreatedAtAscIdAsc(
            Long accountId, Collection<KhathaLedgerEntryType> entryTypes);
}
