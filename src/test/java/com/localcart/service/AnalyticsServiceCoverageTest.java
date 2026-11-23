package com.localcart.service;

import com.localcart.entity.Order;
import com.localcart.entity.OrderItem;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.OrderStatusHistory;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.OrderItemRepository;
import com.localcart.repository.OrderStatusHistoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.TopProductSalesProjection;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceCoverageTest {

    private OrderStatusHistoryRepository historyRepository;
    private OrderItemRepository orderItemRepository;
    private ShopRepository shopRepository;
    private UserRepository userRepository;
    private AnalyticsService service;
    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        historyRepository = mock(OrderStatusHistoryRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        shopRepository = mock(ShopRepository.class);
        userRepository = mock(UserRepository.class);
        service = new AnalyticsService(
                historyRepository, orderItemRepository, shopRepository, userRepository
        );
        securityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void adminOverviewDeduplicatesOrdersAndSeparatesCancelledRevenue() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate to = LocalDate.now();
        User customerA = user(10L, Role.CUSTOMER, true);
        User customerB = user(11L, Role.CUSTOMER, true);
        Shop shop = shop(1L, "Local Fresh", true);
        Order delivered = order(100L, shop, customerA, OrderStatus.DELIVERED, 50.50, 2);
        Order cancelled = order(101L, shop, customerB, OrderStatus.CANCELLED, 30.00, 9);
        Order open = order(102L, shop, customerA, OrderStatus.PLACED, 20.00, -4);

        when(historyRepository.findPlacementEvents(any(), any(), any()))
                .thenReturn(List.of(
                        history(1L, delivered, from.atTime(9, 0)),
                        history(2L, cancelled, from.atTime(10, 0)),
                        history(3L, open, to.atTime(11, 0)),
                        history(4L, delivered, to.atTime(12, 0))
                ));
        when(shopRepository.count()).thenReturn(4L);
        when(shopRepository.findAll()).thenReturn(List.of(shop, shop(2L, "Closed Shop", false)));
        when(userRepository.countByRoleAndActiveTrue(Role.CUSTOMER)).thenReturn(20L);
        when(userRepository.countByRoleAndActiveTrue(Role.SHOPKEEPER)).thenReturn(4L);
        when(userRepository.countByRoleAndActiveTrue(Role.DELIVERY_PARTNER)).thenReturn(7L);

        var response = service.getAdminOverview(from, to);
        var metrics = response.orderMetrics();

        assertEquals(3L, metrics.totalOrders());
        assertEquals(1L, metrics.deliveredOrders());
        assertEquals(1L, metrics.cancelledOrders());
        assertEquals(1L, metrics.openOrders());
        assertEquals(2L, metrics.customersWithOrders());
        assertEquals(2L, metrics.unitsInNonCancelledOrders());
        assertEquals("100.50", metrics.grossOrderValue().toPlainString());
        assertEquals("70.50", metrics.nonCancelledOrderValue().toPlainString());
        assertEquals("50.50", metrics.deliveredOrderValue().toPlainString());
        assertEquals("35.25", metrics.averageNonCancelledOrderValue().toPlainString());
        assertEquals(4L, metrics.shopsCovered());
        assertEquals(1L, response.activeShops());
        assertEquals(20L, response.activeCustomerAccounts());
        assertEquals(4L, response.activeShopkeeperAccounts());
        assertEquals(7L, response.activeDeliveryPartnerAccounts());
    }

    @Test
    void adminDailyUsesEarliestPlacementEventAndEmitsEmptyDates() {
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();
        Shop shop = shop(1L, "Local Fresh", true);
        Order order = order(100L, shop, null, OrderStatus.DELIVERED, 45.0, 1);
        when(historyRepository.findPlacementEvents(any(), any(), any()))
                .thenReturn(List.of(
                        history(2L, order, from.atTime(10, 0)),
                        history(1L, order, from.atTime(9, 0))
                ));

        var daily = service.getAdminDaily(from, to);

        assertEquals(2, daily.size());
        assertEquals(1L, daily.get(0).totalOrders());
        assertEquals("45.00", daily.get(0).nonCancelledOrderValue().toPlainString());
        assertEquals(0L, daily.get(1).totalOrders());
    }

    @Test
    void topShopsSortByNonCancelledRevenueAndClampLimit() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate to = LocalDate.now();
        Shop lower = shop(1L, "Alpha", true);
        Shop higher = shop(2L, "Beta", true);
        Order lowOrder = order(100L, lower, null, OrderStatus.DELIVERED, 60.0, 1);
        Order highOrder = order(101L, higher, null, OrderStatus.PLACED, 100.0, 1);
        Order cancelled = order(102L, lower, null, OrderStatus.CANCELLED, 200.0, 1);
        when(historyRepository.findPlacementEvents(any(), any(), any()))
                .thenReturn(List.of(
                        history(1L, lowOrder, from.atStartOfDay()),
                        history(2L, highOrder, from.plusDays(1).atStartOfDay()),
                        history(3L, cancelled, to.atStartOfDay())
                ));

        var result = service.getAdminTopShops(from, to, 100);

        assertEquals(2, result.size());
        assertEquals("Beta", result.get(0).shopName());
        assertEquals("100.00", result.get(0).nonCancelledOrderValue().toPlainString());
        assertEquals("Alpha", result.get(1).shopName());
        assertEquals(2L, result.get(1).totalOrders());
        assertEquals(1L, result.get(1).cancelledOrders());
    }

    @Test
    void topProductsUsesOnlyNonCancelledOrderIdsAndMapsNullProjectionValuesToZero() {
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();
        Shop shop = shop(1L, "Local Fresh", true);
        Order delivered = order(100L, shop, null, OrderStatus.DELIVERED, 25.0, 1);
        Order cancelled = order(101L, shop, null, OrderStatus.CANCELLED, 10.0, 1);
        when(historyRepository.findPlacementEvents(any(), any(), any()))
                .thenReturn(List.of(
                        history(1L, delivered, from.atStartOfDay()),
                        history(2L, cancelled, to.atStartOfDay())
                ));
        TopProductSalesProjection row = mock(TopProductSalesProjection.class);
        when(row.getShopId()).thenReturn(1L);
        when(row.getShopName()).thenReturn("Local Fresh");
        when(row.getProductId()).thenReturn(88L);
        when(row.getProductName()).thenReturn("Rice");
        when(row.getUnitsSold()).thenReturn(null);
        when(row.getOrderValue()).thenReturn(null);
        when(orderItemRepository.findProductSalesByOrderIds(List.of(100L), OrderStatus.CANCELLED))
                .thenReturn(List.of(row));

        var result = service.getAdminTopProducts(from, to, 10);

        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).unitsSold());
        assertEquals("0.00", result.get(0).orderValue().toPlainString());
    }

    @Test
    void topProductsDoesNotQueryProjectionWhenEveryOrderWasCancelled() {
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();
        Order cancelled = order(101L, shop(1L, "Local Fresh", true), null,
                OrderStatus.CANCELLED, 10.0, 1);
        when(historyRepository.findPlacementEvents(any(), any(), any()))
                .thenReturn(List.of(history(2L, cancelled, from.atStartOfDay())));

        assertTrue(service.getAdminTopProducts(from, to, 10).isEmpty());
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void shopkeeperWithNoShopsGetsEmptyDailySeriesWithoutQueryingHistory() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        User owner = user(15L, Role.SHOPKEEPER, true);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.findByOwnerId(15L)).thenReturn(List.of());
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();

        var result = service.getShopkeeperDaily(from, to);

        assertEquals(2, result.size());
        assertEquals(0L, result.get(0).totalOrders());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void shopkeeperMustBeActiveAndHaveShopkeeperRole() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("inactive@example.com");
        when(userRepository.findByEmail("inactive@example.com"))
                .thenReturn(Optional.of(user(15L, Role.SHOPKEEPER, false)));

        assertThrows(BadRequestException.class,
                () -> service.getShopkeeperOverview(null, null));

        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(16L, Role.CUSTOMER, true)));
        assertThrows(BadRequestException.class,
                () -> service.getShopkeeperDaily(null, null));
    }

    @Test
    void shopkeeperCannotRequestTopProductsForAnotherShop() {
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(user(15L, Role.SHOPKEEPER, true)));
        when(shopRepository.findByOwnerId(15L))
                .thenReturn(List.of(shop(1L, "My Shop", true)));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getShopkeeperTopProducts(999L, null, null, 10));
        verifyNoInteractions(historyRepository);
    }

    private static User user(Long id, Role role, boolean active) {
        User user = new User();
        setId(user, id);
        user.setName("User " + id);
        user.setEmail("user" + id + "@example.com");
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private static Shop shop(Long id, String name, boolean active) {
        Shop shop = new Shop();
        setId(shop, id);
        shop.setName(name);
        shop.setActive(active);
        return shop;
    }

    private static Order order(Long id, Shop shop, User customer,
                               OrderStatus status, double amount, int quantity) {
        Order order = new Order();
        order.setId(id);
        order.setShop(shop);
        order.setCustomer(customer);
        order.setStatus(status);
        order.setTotalAmount(amount);
        if (quantity != 0) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setQuantity(quantity);
            order.getItems().add(item);
        }
        return order;
    }

    private static OrderStatusHistory history(Long id, Order order, LocalDateTime changedAt) {
        OrderStatusHistory history = new OrderStatusHistory();
        setId(history, id);
        history.setOrder(order);
        history.setNewStatus(OrderStatus.PLACED);
        history.setChangedAt(changedAt);
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
