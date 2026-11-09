package com.localcart.repository;

import com.localcart.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderHistoryNotificationRepository
        extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findTop100ByIdGreaterThanOrderByIdAsc(Long id);

    @Query("select coalesce(max(h.id), 0) from OrderStatusHistory h")
    Long findMaximumId();
}
