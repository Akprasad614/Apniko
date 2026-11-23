package com.localcart.service;

import com.localcart.entity.Notification;
import com.localcart.entity.NotificationType;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.NotificationRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationService service;
    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        service = new NotificationService(notificationRepository, userRepository);
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserEmail)
                .thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(activeCustomer()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void unreadCountIsScopedToAuthenticatedUser() {
        when(notificationRepository.countByRecipientUserIdAndReadAtIsNull(10L))
                .thenReturn(4L);

        assertEquals(4L, service.getUnreadCount());
        verify(notificationRepository).countByRecipientUserIdAndReadAtIsNull(10L);
    }

    @Test
    void markAsReadLoadsNotificationOnlyForCurrentRecipient() {
        Notification notification = notification(31L, 10L);
        when(notificationRepository.findByIdAndRecipientUserId(31L, 10L))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        var response = service.markAsRead(31L);

        assertTrue(response.isRead());
        assertNotNull(response.getReadAt());
        verify(notificationRepository).findByIdAndRecipientUserId(31L, 10L);
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsReadDoesNotRevealAnotherUsersNotification() {
        when(notificationRepository.findByIdAndRecipientUserId(32L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.markAsRead(32L));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void alreadyReadNotificationIsNotSavedAgain() {
        Notification notification = notification(33L, 10L);
        notification.setReadAt(LocalDateTime.now().minusHours(1));
        when(notificationRepository.findByIdAndRecipientUserId(33L, 10L))
                .thenReturn(Optional.of(notification));

        var response = service.markAsRead(33L);

        assertTrue(response.isRead());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAllAsReadUpdatesOnlyCurrentRecipient() {
        when(notificationRepository.markAllAsRead(eq(10L), any(LocalDateTime.class)))
                .thenReturn(3);

        assertEquals(3, service.markAllAsRead());
        verify(notificationRepository).markAllAsRead(eq(10L), any(LocalDateTime.class));
    }

    @Test
    void deleteNotificationIsScopedToCurrentRecipient() {
        Notification notification = notification(34L, 10L);
        when(notificationRepository.findByIdAndRecipientUserId(34L, 10L))
                .thenReturn(Optional.of(notification));

        service.deleteNotification(34L);

        verify(notificationRepository).delete(notification);
    }

    @Test
    void missingAuthenticatedUserIsRejected() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, service::getUnreadCount);
        verifyNoInteractions(notificationRepository);
    }

    private User activeCustomer() {
        User user = new User();
        user.setName("Test Customer");
        user.setEmail("customer@example.com");
        user.setRole(Role.CUSTOMER);
        user.setActive(true);
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, 10L);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return user;
    }

    private Notification notification(Long id, Long recipientId) {
        Notification notification = new Notification();
        try {
            var idField = Notification.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(notification, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        notification.setRecipientUserId(recipientId);
        notification.setTitle("Order update");
        notification.setMessage("Your order has been updated");
        notification.setType(NotificationType.ORDER);
        notification.setSourceKey("test-source-" + id);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }
}
