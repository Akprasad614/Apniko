package com.localcart.repository;

import com.localcart.entity.KhathaAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface KhathaAccountRepository extends JpaRepository<KhathaAccount,Long> {
    Optional<KhathaAccount> findByShopIdAndCustomerId(Long shopId, Long customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from KhathaAccount a where a.shop.id=:shopId and a.customer.id=:customerId")
    Optional<KhathaAccount> findByShopAndCustomerForUpdate(@Param("shopId") Long shopId,@Param("customerId") Long customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from KhathaAccount a where a.id=:id")
    Optional<KhathaAccount> findByIdForUpdate(@Param("id") Long id);
    @Query("select a from KhathaAccount a join fetch a.shop where a.customer.id=:customerId order by a.updatedAt desc")
    List<KhathaAccount> findCustomerAccounts(@Param("customerId") Long customerId);
    @Query("select a from KhathaAccount a join fetch a.customer where a.shop.id=:shopId order by a.updatedAt desc")
    List<KhathaAccount> findShopAccounts(@Param("shopId") Long shopId);
    List<KhathaAccount> findAllByOrderByUpdatedAtDesc();
    List<KhathaAccount> findByOutstandingBalanceGreaterThanAndDueDateBetween(
            BigDecimal minimumBalance, LocalDate startDate, LocalDate endDate);
    List<KhathaAccount> findByOutstandingBalanceGreaterThanAndDueDateBefore(
            BigDecimal minimumBalance, LocalDate dueBefore);
}
