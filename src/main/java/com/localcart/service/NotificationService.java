package com.localcart.service;

import com.localcart.dto.NotificationResponse;
import com.localcart.entity.Notification;
import com.localcart.entity.User;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.NotificationRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(
            boolean unreadOnly,
            int page,
            int size) {

        Long userId = getCurrentUser().getId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        PageRequest pageable = PageRequest.of(safePage, safeSize);

        Page<Notification> notifications = unreadOnly
                ? notificationRepository
                    .findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                            userId, pageable)
                : notificationRepository
                    .findByRecipientUserIdOrderByCreatedAtDesc(
                            userId, pageable);

        return notifications.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository
                .countByRecipientUserIdAndReadAtIsNull(
                        getCurrentUser().getId()
                );
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = getCurrentUser().getId();
        Notification notification = notificationRepository
                .findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found"
                ));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllAsRead() {
        return notificationRepository.markAllAsRead(
                getCurrentUser().getId(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        Long userId = getCurrentUser().getId();
        Notification notification = notificationRepository
                .findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found"
                ));
        notificationRepository.delete(notification);
    }

    private User getCurrentUser() {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }

        return userRepository.findByEmail(email)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active user account not found"
                ));
    }
}
