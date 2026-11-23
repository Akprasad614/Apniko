package com.localcart.repository;

import com.localcart.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(
            Long recipientUserId,
            Pageable pageable
    );

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            Long recipientUserId,
            Pageable pageable
    );

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(
            Long id,
            Long recipientUserId
    );

    boolean existsBySourceKey(String sourceKey);

    @Modifying
    @Transactional
    @Query("update Notification n set n.readAt = :readAt " +
            "where n.recipientUserId = :recipientUserId and n.readAt is null")
    int markAllAsRead(
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") LocalDateTime readAt
    );
}
