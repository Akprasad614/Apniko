package com.localcart.service;

import com.localcart.dto.AiInsightSummaryResponse;
import com.localcart.dto.DemandForecastResponse;
import com.localcart.dto.ReplenishmentRecommendationResponse;
import com.localcart.entity.Order;
import com.localcart.entity.OrderItem;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.OrderStatusHistory;
import com.localcart.entity.Product;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.OrderStatusHistoryRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ShopkeeperAiInsightsServiceTest {

    private OrderStatusHistoryRepository historyRepository;
    private ShopInventoryRepository inventoryRepository;
    private ShopRepository shopRepository;
    private UserRepository userRepository;
    private ShopkeeperAiInsightsService service;

    private User shopkeeper;
    private User shopOwner;
    private Shop shop;

    @BeforeEach
    void setUp() {
        historyRepository = mock(OrderStatusHistoryRepository.class);
        inventoryRepository = mock(ShopInventoryRepository.class);
        shopRepository = mock(ShopRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ShopkeeperAiInsightsService(
                historyRepository, inventoryRepository, shopRepository, userRepository
        );

        shopkeeper = mock(User.class);
        shopOwner = mock(User.class);
        shop = mock(Shop.class);

        when(shopkeeper.getId()).thenReturn(7L);
        when(shopkeeper.getRole()).thenReturn(Role.SHOPKEEPER);
        when(shopkeeper.isActive()).thenReturn(true);
        when(shopOwner.getId()).thenReturn(7L);
        when(shop.getId()).thenReturn(1L);
        when(shop.getName()).thenReturn("Test Grocery Shop");
        when(shop.getOwner()).thenReturn(shopOwner);
        when(userRepository.findByEmail("shopkeeper@example.com"))
                .thenReturn(Optional.of(shopkeeper));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "shopkeeper@example.com", "test-password", List.of()
                )
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void productForecastRejectsForecastLongerThanThirtyDays() {
        assertThrows(BadRequestException.class,
                () -> service.getProductForecast(1L, 101L, 31, 28));
        verifyNoInteractions(historyRepository, inventoryRepository, shopRepository, userRepository);
    }

    @Test
    void productForecastRejectsHistoryShorterThanSevenDays() {
        assertThrows(BadRequestException.class,
                () -> service.getProductForecast(1L, 101L, 7, 6));
        verifyNoInteractions(historyRepository, inventoryRepository, shopRepository, userRepository);
    }

    @Test
    void productForecastRejectsHistoryLongerThanNinetyDays() {
        assertThrows(BadRequestException.class,
                () -> service.getProductForecast(1L, 101L, 7, 91));
        verifyNoInteractions(historyRepository, inventoryRepository, shopRepository, userRepository);
    }

    @Test
    void replenishmentRejectsLeadTimeAboveThirtyDays() {
        assertThrows(BadRequestException.class,
                () -> service.getReplenishmentRecommendations(1L, 28, 31));
        verifyNoInteractions(historyRepository, inventoryRepository, shopRepository, userRepository);
    }

    @Test
    void replenishmentRejectsHistoryShorterThanSevenDays() {
        assertThrows(BadRequestException.class,
                () -> service.getReplenishmentRecommendations(1L, 2, 3));
        verifyNoInteractions(historyRepository, inventoryRepository, shopRepository, userRepository);
    }

    @Test
    void summaryRejectsHistoryLongerThanNinetyDays() {
        assertThrows(BadRequestException.class,
                () -> service.getSummary(1L, 120));
        verifyNoInteractions(historyRepository, inventoryRepository, shopRepository, userRepository);
    }

    @Test
    void productForecastBuildsDailyForecastFromRecentPlacementEvents() {
        Product product = product(101L, "Aashirvaad Atta");
        when(inventoryRepository.findByShopIdAndProductId(1L, 101L))
                .thenReturn(Optional.of(inventory(product, 20, 5)));
        when(historyRepository.findPlacementEventsForShops(
                anyList(), eq(OrderStatus.PLACED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        placementEvent(1L, 1, OrderStatus.PLACED, List.of(item(product, 2))),
                        placementEvent(2L, 2, OrderStatus.PLACED, List.of(item(product, 2))),
                        placementEvent(3L, 3, OrderStatus.PLACED, List.of(item(product, 2))),
                        placementEvent(4L, 4, OrderStatus.PLACED, List.of(item(product, 2))),
                        placementEvent(5L, 5, OrderStatus.PLACED, List.of(item(product, 2))),
                        placementEvent(6L, 6, OrderStatus.PLACED, List.of(item(product, 2)))
                ));

        DemandForecastResponse response = service.getProductForecast(1L, 101L, 3, 28);

        assertEquals(1L, response.shopId());
        assertEquals("Test Grocery Shop", response.shopName());
        assertEquals(101L, response.productId());
        assertEquals("Aashirvaad Atta", response.productName());
        assertEquals(28, response.historyDays());
        assertEquals(3, response.forecastDays());
        assertEquals(6, response.activeSalesDays());
        assertEquals(0.43, response.averageDailyUnits(), 0.01);
        assertEquals(1.03, response.predictedDailyUnits(), 0.01);
        assertEquals(3.09, response.predictedUnitsForPeriod(), 0.01);
        assertEquals("MEDIUM", response.confidence());
        assertEquals(3, response.dailyForecast().size());
        assertEquals(1.03, response.dailyForecast().get(0).expectedUnits(), 0.01);
    }

    @Test
    void productForecastUsesDefaultPeriodsWhenArgumentsAreNull() {
        Product product = product(102L, "Rice");
        when(inventoryRepository.findByShopIdAndProductId(1L, 102L))
                .thenReturn(Optional.of(inventory(product, 12, 5)));
        when(historyRepository.findPlacementEventsForShops(
                anyList(), eq(OrderStatus.PLACED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        DemandForecastResponse response = service.getProductForecast(1L, 102L, null, null);

        assertEquals(28, response.historyDays());
        assertEquals(7, response.forecastDays());
        assertEquals(0, response.activeSalesDays());
        assertEquals("LOW", response.confidence());
        assertEquals(0.0, response.predictedDailyUnits());
        assertEquals(7, response.dailyForecast().size());
    }

    @Test
    void productForecastRejectsProductMissingFromShopInventory() {
        when(inventoryRepository.findByShopIdAndProductId(1L, 999L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getProductForecast(1L, 999L, 7, 28));

        verifyNoInteractions(historyRepository);
    }

    @Test
    void demandCalculationIgnoresCancelledAndDuplicateOrderEvents() {
        Product product = product(103L, "Cooking Oil");
        when(inventoryRepository.findByShopIdAndProductId(1L, 103L))
                .thenReturn(Optional.of(inventory(product, 10, 5)));
        OrderStatusHistory firstEvent = placementEvent(
                50L, 1, OrderStatus.PLACED, List.of(item(product, 5)));
        OrderStatusHistory duplicateEvent = placementEvent(
                50L, 2, OrderStatus.PLACED, List.of(item(product, 100)));
        OrderStatusHistory cancelledOrder = placementEvent(
                51L, 3, OrderStatus.CANCELLED, List.of(item(product, 40)));
        when(historyRepository.findPlacementEventsForShops(
                anyList(), eq(OrderStatus.PLACED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(firstEvent, duplicateEvent, cancelledOrder));

        DemandForecastResponse response = service.getProductForecast(1L, 103L, 7, 28);

        assertEquals(1, response.activeSalesDays());
        assertEquals(0.18, response.averageDailyUnits(), 0.01);
        assertEquals(0.43, response.predictedDailyUnits(), 0.01);
    }

    @Test
    void replenishmentReturnsEmptyListWhenShopHasNoInventory() {
        when(inventoryRepository.findByShopId(1L)).thenReturn(List.of());

        List<ReplenishmentRecommendationResponse> response =
                service.getReplenishmentRecommendations(1L, 28, 3);

        assertTrue(response.isEmpty());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void replenishmentSortsReorderNowBeforeSoonBeforeHealthy() {
        Product urgentProduct = product(201L, "Urgent Product");
        Product soonProduct = product(202L, "Soon Product");
        Product healthyProduct = product(203L, "Healthy Product");

        when(inventoryRepository.findByShopId(1L)).thenReturn(List.of(
                inventory(healthyProduct, 30, 5),
                inventory(soonProduct, 20, 5),
                inventory(urgentProduct, 10, 5)
        ));

        List<OrderStatusHistory> events = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            events.add(placementEvent(300L + day, day, OrderStatus.PLACED, List.of(
                    item(urgentProduct, 7),
                    item(soonProduct, 7),
                    item(healthyProduct, 7)
            )));
        }
        when(historyRepository.findPlacementEventsForShops(
                anyList(), eq(OrderStatus.PLACED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(events);

        List<ReplenishmentRecommendationResponse> response =
                service.getReplenishmentRecommendations(1L, 28, 3);

        assertEquals(3, response.size());
        assertEquals(201L, response.get(0).productId());
        assertEquals("REORDER_NOW", response.get(0).status());
        assertEquals(202L, response.get(1).productId());
        assertEquals("REORDER_SOON", response.get(1).status());
        assertEquals(203L, response.get(2).productId());
        assertEquals("HEALTHY", response.get(2).status());
        assertTrue(response.get(0).suggestedReorderQuantity() > 0);
        assertTrue(response.get(1).estimatedDaysOfCover() > 3.0);
    }

    @Test
    void replenishmentUsesDefaultLowStockThresholdForLegacyInventory() {
        Product product = product(204L, "Legacy Inventory Product");
        ShopInventory inventory = inventory(product, 5, null);
        when(inventoryRepository.findByShopId(1L)).thenReturn(List.of(inventory));
        when(historyRepository.findPlacementEventsForShops(
                anyList(), eq(OrderStatus.PLACED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        List<ReplenishmentRecommendationResponse> response =
                service.getReplenishmentRecommendations(1L, 28, 3);

        assertEquals(1, response.size());
        assertEquals(5, response.get(0).lowStockThreshold());
        assertEquals("REORDER_NOW", response.get(0).status());
        assertNull(response.get(0).estimatedDaysOfCover());
    }

    @Test
    void summaryAggregatesPredictedDemandAndProductsNeedingAttention() {
        Product product = product(205L, "Summary Product");
        when(inventoryRepository.findByShopId(1L))
                .thenReturn(List.of(inventory(product, 10, 5)));
        List<OrderStatusHistory> events = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            events.add(placementEvent(400L + day, day, OrderStatus.PLACED,
                    List.of(item(product, 7))));
        }
        when(historyRepository.findPlacementEventsForShops(
                anyList(), eq(OrderStatus.PLACED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(events);

        AiInsightSummaryResponse response = service.getSummary(1L, 28);

        assertEquals(1L, response.shopId());
        assertEquals(1, response.productsAnalysed());
        assertEquals(1, response.productsRequiringAttention());
        assertEquals(29.4, response.estimatedDemandNextSevenDays(), 0.1);
        assertEquals(1, response.recommendations().size());
        assertEquals("REORDER_NOW", response.recommendations().get(0).status());
    }

    @Test
    void shopkeeperCannotReadAnotherOwnersShopInsights() {
        User differentOwner = mock(User.class);
        when(differentOwner.getId()).thenReturn(999L);
        when(shop.getOwner()).thenReturn(differentOwner);

        assertThrows(ResourceNotFoundException.class,
                () -> service.getReplenishmentRecommendations(1L, 28, 3));
        verifyNoInteractions(inventoryRepository, historyRepository);
    }

    @Test
    void inactiveShopkeeperAccountCannotReadInsights() {
        when(shopkeeper.isActive()).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.getReplenishmentRecommendations(1L, 28, 3));
        verifyNoInteractions(shopRepository, inventoryRepository, historyRepository);
    }

    private ShopInventory inventory(Product product, int stock, Integer threshold) {
        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setStockQuantity(stock);
        inventory.setLowStockThreshold(threshold);
        return inventory;
    }

    private Product product(Long id, String name) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn(name);
        return product;
    }

    private OrderItem item(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private OrderStatusHistory placementEvent(
            Long orderId,
            int daysAgo,
            OrderStatus orderStatus,
            List<OrderItem> items) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(orderStatus);
        order.setShop(shop);
        order.setItems(items);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setNewStatus(OrderStatus.PLACED);
        history.setChangedAt(LocalDate.now().minusDays(daysAgo).atTime(12, 0));
        return history;
    }
}
