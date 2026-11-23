package com.localcart.repository;

import com.localcart.entity.ProductCatalogueSourceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductCatalogueSourceRecordRepository
        extends JpaRepository<ProductCatalogueSourceRecord, Long> {
    Optional<ProductCatalogueSourceRecord> findBySourceNameAndSourceRecordId(
            String sourceName, String sourceRecordId);
}
