package com.localcart.service;

import com.localcart.entity.*;
import com.localcart.repository.NotificationRepository;
import com.localcart.repository.NotificationSyncStateRepository;
import com.localcart.repository.OrderHistoryNotificationRepository;
import com.localcart.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderNotificationSyncService {

    private static final long SYNC_STATE_ID = 1L;

    private final OrderHistoryNotificationRepository historyRepository;
    private final NotificationSyncStateRepository syncStateRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public OrderNotificationSyncService(
            OrderHistoryNotificationRepository historyRepository,
            NotificationSyncStateRepository syncStateRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.syncStateRepository = syncStateRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Initialize the singleton cursor after application startup. If the
     * scheduled poller races this listener, insertIfAbsent() is safe because
     * the database enforces the singleton key atomically.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeSyncCursor() {
        ensureSyncState();
    }

    /**
     * Poll committed order-history records. Cursor and notifications are
     * updated in one transaction. Existing cursor rows are never reset.
     */
    @Scheduled(fixedDelayString = "${localcart.notifications.poll-delay-ms:5000}")
    @Transactional
    public void processNewOrderEvents() {
        NotificationSyncState state = ensureSyncState();

        List<OrderStatusHistory> events = historyRepository
                .findTop100ByIdGreaterThanOrderByIdAsc(
                        state.getLastProcessedHistoryId()
                );

        for (OrderStatusHistory event : events) {
            createNotificationsFor(event);
            state.setLastProcessedHistoryId(event.getId());
        }

        if (!events.isEmpty()) {
            syncStateRepository.save(state);
        }
    }

    /**
     * Check first for the normal path, but use a database-level atomic
     * insert-if-absent when the row is missing. Two callers can safely race.
     */
    private NotificationSyncState ensureSyncState() {
        return syncStateRepository.findById(SYNC_STATE_ID)
                .orElseGet(() -> {
                    Long maximumHistoryId = historyRepository.findMaximumId();
                    syncStateRepository.insertIfAbsent(
                            SYNC_STATE_ID,
                            maximumHistoryId == null ? 0L : maximumHistoryId
                    );
                    return syncStateRepository.findLockedById(SYNC_STATE_ID)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Unable to initialize notification sync state"
                            ));
                });
    }

    private void createNotificationsFor(OrderStatusHistory history) {
        Order order = history.getOrder();
        if (order == null || history.getNewStatus() == null) {
            return;
        }

        OrderStatus status = history.getNewStatus();
        String shopName = order.getShop() == null
                ? "the shop"
                : order.getShop().getName();
        String orderNumber = "Order #" + order.getId();

        if (order.getCustomer() != null) {
            createIfMissing(
                    history,
                    order.getCustomer(),
                    customerTitle(status),
                    customerMessage(status, orderNumber, shopName),
                    NotificationType.ORDER
            );
        }

        if ((status == OrderStatus.PLACED || status == OrderStatus.CANCELLED)
                && order.getShop() != null
                && order.getShop().getOwner() != null) {
            String title = status == OrderStatus.PLACED
                    ? "New order received"
                    : "Order cancelled";
            String message = status == OrderStatus.PLACED
                    ? orderNumber + " has been placed at " + shopName + "."
                    : orderNumber + " has been cancelled.";
            createIfMissing(
                    history,
                    order.getShop().getOwner(),
                    title,
                    message,
                    NotificationType.ORDER
            );
        }

        if (status == OrderStatus.READY
                && order.getDeliveryMode() == DeliveryMode.PLATFORM_DELIVERY) {
            List<User> partners = userRepository.findByRole(Role.DELIVERY_PARTNER);
            for (User partner : partners) {
                if (partner.isActive()) {
                    createIfMissing(
                            history,
                            partner,
                            "Delivery available",
                            orderNumber + " is ready for platform delivery from "
                                    + shopName + ".",
                            NotificationType.DELIVERY
                    );
                }
            }
        }
    }

    private void createIfMissing(
            OrderStatusHistory history,
            User recipient,
            String title,
            String message,
            NotificationType type) {

        String sourceKey = "ORDER-HISTORY-" + history.getId()
                + "-USER-" + recipient.getId();

        if (notificationRepository.existsBySourceKey(sourceKey)) {
            return;
        }

        Notification notification = new Notification();
        notification.setRecipientUserId(recipient.getId());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRelatedOrderId(history.getOrder().getId());
        notification.setSourceKey(sourceKey);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private String customerTitle(OrderStatus status) {
        return switch (status) {
            case PLACED -> "Order placed";
            case ACCEPTED -> "Order accepted";
            case PREPARING -> "Order being prepared";
            case READY -> "Order ready";
            case OUT_FOR_DELIVERY -> "Order out for delivery";
            case DELIVERED -> "Order delivered";
            case CANCELLED -> "Order cancelled";
        };
    }

    private String customerMessage(
            OrderStatus status,
            String orderNumber,
            String shopName) {
        return switch (status) {
            case PLACED -> orderNumber + " has been placed with " + shopName + ".";
            case ACCEPTED -> shopName + " has accepted " + orderNumber + ".";
            case PREPARING -> shopName + " is preparing " + orderNumber + ".";
            case READY -> orderNumber + " is ready for the next step.";
            case OUT_FOR_DELIVERY -> orderNumber + " is on its way to you.";
            case DELIVERED -> orderNumber + " has been delivered successfully.";
            case CANCELLED -> orderNumber + " has been cancelled.";
        };
    }
}
