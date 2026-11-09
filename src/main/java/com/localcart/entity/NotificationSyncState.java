package com.localcart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_sync_state")
public class NotificationSyncState {

    @Id
    private Long id;

    @Column(name = "last_processed_history_id", nullable = false)
    private Long lastProcessedHistoryId;

    public NotificationSyncState() {
    }

    public NotificationSyncState(Long id, Long lastProcessedHistoryId) {
        this.id = id;
        this.lastProcessedHistoryId = lastProcessedHistoryId;
    }

    public Long getId() {
        return id;
    }

    public Long getLastProcessedHistoryId() {
        return lastProcessedHistoryId;
    }

    public void setLastProcessedHistoryId(Long lastProcessedHistoryId) {
        this.lastProcessedHistoryId = lastProcessedHistoryId;
    }
}
