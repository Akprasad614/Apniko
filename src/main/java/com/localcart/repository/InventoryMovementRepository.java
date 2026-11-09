package com.localcart.repository;

import com.localcart.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryMovementRepository
        extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findByInventoryIdAndShopIdOrderByCreatedAtDescIdDesc(
            Long inventoryId,
            Long shopId
    );
}
