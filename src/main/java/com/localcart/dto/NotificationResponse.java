package com.localcart.dto;

import com.localcart.entity.Notification;
import com.localcart.entity.NotificationType;

import java.time.LocalDateTime;

public class NotificationResponse {

    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private Long relatedOrderId;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private boolean read;

    public NotificationResponse() {
    }

    public static NotificationResponse from(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.id = notification.getId();
        response.title = notification.getTitle();
        response.message = notification.getMessage();
        response.type = notification.getType();
        response.relatedOrderId = notification.getRelatedOrderId();
        response.createdAt = notification.getCreatedAt();
        response.readAt = notification.getReadAt();
        response.read = notification.getReadAt() != null;
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public NotificationType getType() {
        return type;
    }

    public Long getRelatedOrderId() {
        return relatedOrderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return read;
    }
}
