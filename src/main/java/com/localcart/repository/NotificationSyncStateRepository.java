package com.localcart.repository;

import com.localcart.entity.NotificationSyncState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface NotificationSyncStateRepository
        extends JpaRepository<NotificationSyncState, Long> {

    /**
     * Creates the singleton cursor row only when it does not already exist.
     * MySQL executes this as one atomic statement, preventing competing
     * startup/poller transactions from inserting the same primary key.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_sync_state (id, last_processed_history_id)
            VALUES (:id, :historyId)
            ON DUPLICATE KEY UPDATE id = VALUES(id)
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") Long id,
            @Param("historyId") Long historyId
    );

    /**
     * A pessimistic lock forces a current database read after an upsert.
     * This avoids reading a stale REPEATABLE_READ snapshot when another
     * transaction created the singleton row first.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from NotificationSyncState state where state.id = :id")
    Optional<NotificationSyncState> findLockedById(@Param("id") Long id);
}
