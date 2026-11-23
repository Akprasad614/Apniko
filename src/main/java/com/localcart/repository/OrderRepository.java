package com.localcart.repository;

import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByShopId(Long shopId);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.items item
            LEFT JOIN FETCH item.product product
            LEFT JOIN FETCH product.photo
            WHERE o.customer.id = :customerId
            """)
    List<Order> findByCustomerId(@Param("customerId") Long customerId);

    List<Order> findByShopOwnerId(Long ownerId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByStatusAndDeliveryPartnerIsNull(
            OrderStatus status
    );

    List<Order> findByDeliveryPartnerId(Long deliveryPartnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(
            @Param("id") Long id
    );
}