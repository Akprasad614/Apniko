package com.localcart.repository;

import com.localcart.entity.OrderItem;
import com.localcart.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            SELECT s.id AS shopId,
                   s.name AS shopName,
                   p.id AS productId,
                   p.name AS productName,
                   SUM(oi.quantity) AS unitsSold,
                   SUM(oi.price * oi.quantity) AS orderValue
            FROM OrderItem oi
            JOIN oi.order o
            JOIN o.shop s
            JOIN oi.product p
            WHERE o.id IN :orderIds
              AND o.status <> :cancelledStatus
            GROUP BY s.id, s.name, p.id, p.name
            ORDER BY SUM(oi.quantity) DESC, SUM(oi.price * oi.quantity) DESC
            """)
    List<TopProductSalesProjection> findProductSalesByOrderIds(
            @Param("orderIds") List<Long> orderIds,
            @Param("cancelledStatus") OrderStatus cancelledStatus
    );
}
