package com.localcart.repository;

import com.localcart.entity.OrderStatus;
import com.localcart.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderStatusHistoryRepository
        extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrder_IdOrderByChangedAtAscIdAsc(Long orderId);

    /**
     * Returns the initial PLACED event for each order in the requested date range.
     * Fetching the order and shop keeps the analytics calculation inside one query.
     */
    @Query("""
            SELECT h
            FROM OrderStatusHistory h
            JOIN FETCH h.order o
            JOIN FETCH o.shop s
            LEFT JOIN FETCH o.customer c
            WHERE h.newStatus = :placedStatus
              AND h.changedAt >= :startAt
              AND h.changedAt < :endAt
            ORDER BY h.changedAt ASC, h.id ASC
            """)
    List<OrderStatusHistory> findPlacementEvents(
            @Param("placedStatus") OrderStatus placedStatus,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query("""
            SELECT h
            FROM OrderStatusHistory h
            JOIN FETCH h.order o
            JOIN FETCH o.shop s
            LEFT JOIN FETCH o.customer c
            WHERE h.newStatus = :placedStatus
              AND h.changedAt >= :startAt
              AND h.changedAt < :endAt
              AND s.id IN :shopIds
            ORDER BY h.changedAt ASC, h.id ASC
            """)
    List<OrderStatusHistory> findPlacementEventsForShops(
            @Param("shopIds") List<Long> shopIds,
            @Param("placedStatus") OrderStatus placedStatus,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
