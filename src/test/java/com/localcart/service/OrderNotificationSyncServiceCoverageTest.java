package com.localcart.service;

import com.localcart.entity.DeliveryMode;
import com.localcart.entity.Notification;
import com.localcart.entity.NotificationSyncState;
import com.localcart.entity.NotificationType;
import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.OrderStatusHistory;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.repository.NotificationRepository;
import com.localcart.repository.NotificationSyncStateRepository;
import com.localcart.repository.OrderHistoryNotificationRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderNotificationSyncServiceCoverageTest {

    private OrderHistoryNotificationRepository historyRepository;
    private NotificationSyncStateRepository stateRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private OrderNotificationSyncService service;

    @BeforeEach
    void setUp() {
        historyRepository = mock(OrderHistoryNotificationRepository.class);
        stateRepository = mock(NotificationSyncStateRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        service = new OrderNotificationSyncService(
                historyRepository, stateRepository, notificationRepository, userRepository
        );
    }

    @Test
    void existingCursorProcessesPlacedEventForCustomerAndShopOwner() {
        NotificationSyncState state = new NotificationSyncState(1L, 10L);
        User customer = user(20L, Role.CUSTOMER, true);
        User owner = user(30L, Role.SHOPKEEPER, true);
        Shop shop = shop(4L, "Local Fresh", owner);
        Order order = order(501L, shop, customer, DeliveryMode.SHOP_DELIVERY);
        OrderStatusHistory event = history(11L, order, OrderStatus.PLACED);

        when(stateRepository.findById(1L)).thenReturn(Optional.of(state));
        when(historyRepository.findTop100ByIdGreaterThanOrderByIdAsc(10L))
                .thenReturn(List.of(event));
        when(notificationRepository.existsBySourceKey(anyString())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.processNewOrderEvents();

        assertEquals(11L, state.getLastProcessedHistoryId());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertTrue(saved.stream().anyMatch(n -> n.getRecipientUserId().equals(20L)
                && "Order placed".equals(n.getTitle())
                && n.getType() == NotificationType.ORDER));
        assertTrue(saved.stream().anyMatch(n -> n.getRecipientUserId().equals(30L)
                && "New order received".equals(n.getTitle())));
        assertTrue(saved.stream().allMatch(n -> n.getRelatedOrderId().equals(501L)));
        verify(stateRepository).save(state);
    }

    @Test
    void readyPlatformOrderNotifiesCustomerAndOnlyActiveDeliveryPartners() {
        NotificationSyncState state = new NotificationSyncState(1L, 3L);
        User customer = user(20L, Role.CUSTOMER, true);
        User activePartner = user(41L, Role.DELIVERY_PARTNER, true);
        User inactivePartner = user(42L, Role.DELIVERY_PARTNER, false);
        Order order = order(600L, shop(4L, "Local Fresh", null), customer,
                DeliveryMode.PLATFORM_DELIVERY);
        OrderStatusHistory event = history(4L, order, OrderStatus.READY);

        when(stateRepository.findById(1L)).thenReturn(Optional.of(state));
        when(historyRepository.findTop100ByIdGreaterThanOrderByIdAsc(3L))
                .thenReturn(List.of(event));
        when(userRepository.findByRole(Role.DELIVERY_PARTNER))
                .thenReturn(List.of(activePartner, inactivePartner));
        when(notificationRepository.existsBySourceKey(anyString())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.processNewOrderEvents();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(n ->
                n.getRecipientUserId().equals(20L) && "Order ready".equals(n.getTitle())));
        assertTrue(captor.getAllValues().stream().anyMatch(n ->
                n.getRecipientUserId().equals(41L)
                        && n.getType() == NotificationType.DELIVERY));
        assertFalse(captor.getAllValues().stream().anyMatch(n ->
                n.getRecipientUserId().equals(42L)));
        verify(userRepository).findByRole(Role.DELIVERY_PARTNER);
    }

    @Test
    void alreadyCreatedNotificationIsNotDuplicated() {
        NotificationSyncState state = new NotificationSyncState(1L, 5L);
        User customer = user(20L, Role.CUSTOMER, true);
        Order order = order(601L, null, customer, DeliveryMode.SHOP_DELIVERY);
        OrderStatusHistory event = history(6L, order, OrderStatus.ACCEPTED);

        when(stateRepository.findById(1L)).thenReturn(Optional.of(state));
        when(historyRepository.findTop100ByIdGreaterThanOrderByIdAsc(5L))
                .thenReturn(List.of(event));
        when(notificationRepository.existsBySourceKey("ORDER-HISTORY-6-USER-20"))
                .thenReturn(true);

        service.processNewOrderEvents();

        verify(notificationRepository, never()).save(any(Notification.class));
        assertEquals(6L, state.getLastProcessedHistoryId());
        verify(stateRepository).save(state);
    }

    @Test
    void nullOrderAndNullStatusEventsAreSkippedButCursorStillAdvances() {
        NotificationSyncState state = new NotificationSyncState(1L, 6L);
        OrderStatusHistory noOrder = history(7L, null, OrderStatus.PLACED);
        OrderStatusHistory noStatus = history(8L, order(602L, null, null,
                DeliveryMode.SHOP_DELIVERY), null);

        when(stateRepository.findById(1L)).thenReturn(Optional.of(state));
        when(historyRepository.findTop100ByIdGreaterThanOrderByIdAsc(6L))
                .thenReturn(List.of(noOrder, noStatus));

        service.processNewOrderEvents();

        assertEquals(8L, state.getLastProcessedHistoryId());
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(stateRepository).save(state);
    }

    @Test
    void customerNotificationMessagesCoverEveryOrderStatus() {
        NotificationSyncState state = new NotificationSyncState(1L, 20L);
        User customer = user(20L, Role.CUSTOMER, true);
        java.util.ArrayList<OrderStatusHistory> events = new java.util.ArrayList<>();
        long eventId = 21L;
        for (OrderStatus status : OrderStatus.values()) {
            Order order = order(700L + eventId, null, customer, DeliveryMode.SHOP_DELIVERY);
            events.add(history(eventId++, order, status));
        }
        when(stateRepository.findById(1L)).thenReturn(Optional.of(state));
        when(historyRepository.findTop100ByIdGreaterThanOrderByIdAsc(20L)).thenReturn(events);
        when(notificationRepository.existsBySourceKey(anyString())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.processNewOrderEvents();

        verify(notificationRepository, times(OrderStatus.values().length))
                .save(any(Notification.class));
        assertEquals(20L + OrderStatus.values().length, state.getLastProcessedHistoryId());
    }

    @Test
    void missingCursorIsCreatedUsingMaximumHistoryId() {
        NotificationSyncState initialized = new NotificationSyncState(1L, 25L);
        when(stateRepository.findById(1L)).thenReturn(Optional.empty());
        when(historyRepository.findMaximumId()).thenReturn(25L);
        when(stateRepository.findLockedById(1L)).thenReturn(Optional.of(initialized));
        when(historyRepository.findTop100ByIdGreaterThanOrderByIdAsc(25L))
                .thenReturn(List.of());

        service.processNewOrderEvents();

        verify(stateRepository).insertIfAbsent(1L, 25L);
        verify(stateRepository).findLockedById(1L);
        verify(stateRepository, never()).save(any(NotificationSyncState.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void missingCursorUsesZeroWhenHistoryTableIsEmpty() {
        NotificationSyncState initialized = new NotificationSyncState(1L, 0L);
        when(stateRepository.findById(1L)).thenReturn(Optional.empty());
        when(historyRepository.findMaximumId()).thenReturn(null);
        when(stateRepository.findLockedById(1L)).thenReturn(Optional.of(initialized));
        when(historyRepository.findTop100ByIdGreaterThanOrderByIdAsc(0L))
                .thenReturn(List.of());

        service.initializeSyncCursor();

        verify(stateRepository).insertIfAbsent(1L, 0L);
        verify(stateRepository).findLockedById(1L);
    }

    @Test
    void existingCursorIsNotResetDuringStartupInitialization() {
        NotificationSyncState existing = new NotificationSyncState(1L, 99L);
        when(stateRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.initializeSyncCursor();

        verify(historyRepository, never()).findMaximumId();
        verify(stateRepository, never()).insertIfAbsent(anyLong(), anyLong());
        assertEquals(99L, existing.getLastProcessedHistoryId());
    }

    private static User user(Long id, Role role, boolean active) {
        User user = new User();
        setId(user, id);
        user.setName("Test " + role.name());
        user.setEmail(role.name().toLowerCase() + id + "@example.com");
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private static Shop shop(Long id, String name, User owner) {
        Shop shop = new Shop();
        setId(shop, id);
        shop.setName(name);
        shop.setOwner(owner);
        shop.setActive(true);
        return shop;
    }

    private static Order order(Long id, Shop shop, User customer, DeliveryMode mode) {
        Order order = new Order();
        order.setId(id);
        order.setShop(shop);
        order.setCustomer(customer);
        order.setDeliveryMode(mode);
        return order;
    }

    private static OrderStatusHistory history(Long id, Order order, OrderStatus status) {
        OrderStatusHistory history = new OrderStatusHistory();
        setId(history, id);
        history.setOrder(order);
        history.setNewStatus(status);
        return history;
    }

    private static void setId(Object target, Long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
