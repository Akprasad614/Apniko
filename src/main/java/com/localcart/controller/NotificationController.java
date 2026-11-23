package com.localcart.controller;

import com.localcart.dto.NotificationResponse;
import com.localcart.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                notificationService.getMyNotifications(unreadOnly, page, size)
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(
                Map.of("unreadCount", notificationService.getUnreadCount())
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(
                notificationService.markAsRead(notificationId)
        );
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        return ResponseEntity.ok(
                Map.of("updatedCount", notificationService.markAllAsRead())
        );
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }
}
