package com.localcart.service;

import com.localcart.dto.*;
import com.localcart.entity.*;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.OrderItemRepository;
import com.localcart.repository.OrderStatusHistoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.TopProductSalesProjection;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final OrderStatusHistoryRepository historyRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public AnalyticsService(
            OrderStatusHistoryRepository historyRepository,
            OrderItemRepository orderItemRepository,
            ShopRepository shopRepository,
            UserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.orderItemRepository = orderItemRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsOverviewResponse getAdminOverview(LocalDate from, LocalDate to) {
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        List<OrderStatusHistory> events = getAllPlacementEvents(range);
        AnalyticsOverviewResponse metrics = buildOverview(events, range, shopRepository.count());

        long activeShops = shopRepository.findAll().stream().filter(Shop::isActive).count();
        return new AdminAnalyticsOverviewResponse(
                metrics,
                activeShops,
                userRepository.countByRoleAndActiveTrue(Role.CUSTOMER),
                userRepository.countByRoleAndActiveTrue(Role.SHOPKEEPER),
                userRepository.countByRoleAndActiveTrue(Role.DELIVERY_PARTNER)
        );
    }

    @Transactional(readOnly = true)
    public List<DailyAnalyticsResponse> getAdminDaily(LocalDate from, LocalDate to) {
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        return buildDaily(getAllPlacementEvents(range), range);
    }

    @Transactional(readOnly = true)
    public List<TopShopAnalyticsResponse> getAdminTopShops(
            LocalDate from, LocalDate to, int requestedLimit) {
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        return buildTopShops(getAllPlacementEvents(range), requestedLimit);
    }

    @Transactional(readOnly = true)
    public List<TopProductAnalyticsResponse> getAdminTopProducts(
            LocalDate from, LocalDate to, int requestedLimit) {
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        return buildTopProducts(getAllPlacementEvents(range), requestedLimit);
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getShopkeeperOverview(
            LocalDate from, LocalDate to) {
        List<Shop> shops = getCurrentShopkeeperShops();
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        List<OrderStatusHistory> events = getPlacementEventsForShops(shops, range);
        return buildOverview(events, range, shops.size());
    }

    @Transactional(readOnly = true)
    public List<DailyAnalyticsResponse> getShopkeeperDaily(LocalDate from, LocalDate to) {
        List<Shop> shops = getCurrentShopkeeperShops();
        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        return buildDaily(getPlacementEventsForShops(shops, range), range);
    }

    @Transactional(readOnly = true)
    public List<TopProductAnalyticsResponse> getShopkeeperTopProducts(
            Long shopId, LocalDate from, LocalDate to, int requestedLimit) {
        List<Shop> shops = getCurrentShopkeeperShops();
        List<Shop> selectedShops = shops;

        if (shopId != null) {
            selectedShops = shops.stream()
                    .filter(shop -> shop.getId().equals(shopId))
                    .toList();
            if (selectedShops.isEmpty()) {
                throw new ResourceNotFoundException("Shop not found in your shopkeeper account");
            }
        }

        AnalyticsDateRange range = AnalyticsDateRange.resolve(from, to);
        return buildTopProducts(getPlacementEventsForShops(selectedShops, range), requestedLimit);
    }

    private List<OrderStatusHistory> getAllPlacementEvents(AnalyticsDateRange range) {
        return historyRepository.findPlacementEvents(
                OrderStatus.PLACED, range.startInclusive(), range.endExclusive()
        );
    }

    private List<OrderStatusHistory> getPlacementEventsForShops(
            List<Shop> shops, AnalyticsDateRange range) {
        if (shops.isEmpty()) {
            return List.of();
        }
        List<Long> shopIds = shops.stream().map(Shop::getId).toList();
        return historyRepository.findPlacementEventsForShops(
                shopIds, OrderStatus.PLACED, range.startInclusive(), range.endExclusive()
        );
    }

    private AnalyticsOverviewResponse buildOverview(
            List<OrderStatusHistory> events,
            AnalyticsDateRange range,
            long shopsCovered) {

        List<Order> orders = uniqueOrders(events);
        long delivered = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED).count();
        long cancelled = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.CANCELLED).count();
        long open = orders.size() - delivered - cancelled;

        BigDecimal grossValue = BigDecimal.ZERO;
        BigDecimal nonCancelledValue = BigDecimal.ZERO;
        BigDecimal deliveredValue = BigDecimal.ZERO;
        long units = 0;
        Set<Long> customerIds = new HashSet<>();

        for (Order order : orders) {
            BigDecimal orderValue = money(order.getTotalAmount());
            grossValue = grossValue.add(orderValue);

            if (order.getCustomer() != null) {
                customerIds.add(order.getCustomer().getId());
            }

            if (order.getStatus() != OrderStatus.CANCELLED) {
                nonCancelledValue = nonCancelledValue.add(orderValue);
                if (order.getItems() != null) {
                    for (OrderItem item : order.getItems()) {
                        units += Math.max(0, item.getQuantity());
                    }
                }
            }

            if (order.getStatus() == OrderStatus.DELIVERED) {
                deliveredValue = deliveredValue.add(orderValue);
            }
        }

        long nonCancelledCount = orders.size() - cancelled;
        BigDecimal average = nonCancelledCount == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : nonCancelledValue.divide(
                        BigDecimal.valueOf(nonCancelledCount), 2, RoundingMode.HALF_UP
                );

        return new AnalyticsOverviewResponse(
                range.from(), range.to(), orders.size(), delivered, cancelled, open,
                customerIds.size(), units,
                scale(grossValue), scale(nonCancelledValue), scale(deliveredValue),
                average, shopsCovered
        );
    }

    private List<DailyAnalyticsResponse> buildDaily(
            List<OrderStatusHistory> events,
            AnalyticsDateRange range) {

        Map<Long, OrderStatusHistory> uniqueEvents = new LinkedHashMap<>();
        events.stream()
                .sorted(Comparator.comparing(OrderStatusHistory::getChangedAt)
                        .thenComparing(OrderStatusHistory::getId))
                .forEach(event -> uniqueEvents.putIfAbsent(event.getOrder().getId(), event));

        Map<LocalDate, List<Order>> ordersByDate = new HashMap<>();
        for (OrderStatusHistory event : uniqueEvents.values()) {
            ordersByDate.computeIfAbsent(event.getChangedAt().toLocalDate(), ignored -> new ArrayList<>())
                    .add(event.getOrder());
        }

        List<DailyAnalyticsResponse> result = new ArrayList<>();
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            List<Order> orders = ordersByDate.getOrDefault(date, List.of());
            long delivered = orders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.DELIVERED).count();
            long cancelled = orders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.CANCELLED).count();
            BigDecimal value = orders.stream()
                    .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                    .map(order -> money(order.getTotalAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.add(new DailyAnalyticsResponse(
                    date, orders.size(), delivered, cancelled, scale(value)
            ));
        }
        return result;
    }

    private List<TopShopAnalyticsResponse> buildTopShops(
            List<OrderStatusHistory> events, int requestedLimit) {
        int limit = safeLimit(requestedLimit);
        Map<Long, ShopAccumulator> totals = new HashMap<>();

        for (Order order : uniqueOrders(events)) {
            Shop shop = order.getShop();
            ShopAccumulator accumulator = totals.computeIfAbsent(
                    shop.getId(), ignored -> new ShopAccumulator(shop.getId(), shop.getName())
            );
            accumulator.totalOrders++;
            if (order.getStatus() == OrderStatus.DELIVERED) {
                accumulator.deliveredOrders++;
            }
            if (order.getStatus() == OrderStatus.CANCELLED) {
                accumulator.cancelledOrders++;
            } else {
                accumulator.value = accumulator.value.add(money(order.getTotalAmount()));
            }
        }

        return totals.values().stream()
                .sorted(Comparator.comparing((ShopAccumulator item) -> item.value).reversed()
                        .thenComparing(item -> item.shopName))
                .limit(limit)
                .map(item -> new TopShopAnalyticsResponse(
                        item.shopId, item.shopName, item.totalOrders,
                        item.deliveredOrders, item.cancelledOrders, scale(item.value)
                ))
                .toList();
    }

    private List<TopProductAnalyticsResponse> buildTopProducts(
            List<OrderStatusHistory> events, int requestedLimit) {
        int limit = safeLimit(requestedLimit);
        List<Long> orderIds = uniqueOrders(events).stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .map(Order::getId)
                .toList();
        if (orderIds.isEmpty()) {
            return List.of();
        }

        return orderItemRepository.findProductSalesByOrderIds(orderIds, OrderStatus.CANCELLED)
                .stream()
                .limit(limit)
                .map(this::toTopProductResponse)
                .toList();
    }

    private TopProductAnalyticsResponse toTopProductResponse(TopProductSalesProjection row) {
        return new TopProductAnalyticsResponse(
                row.getShopId(), row.getShopName(), row.getProductId(), row.getProductName(),
                row.getUnitsSold() == null ? 0 : row.getUnitsSold(),
                scale(money(row.getOrderValue() == null ? 0.0 : row.getOrderValue()))
        );
    }

    private List<Order> uniqueOrders(List<OrderStatusHistory> events) {
        Map<Long, Order> unique = new LinkedHashMap<>();
        for (OrderStatusHistory event : events) {
            unique.putIfAbsent(event.getOrder().getId(), event.getOrder());
        }
        return new ArrayList<>(unique.values());
    }

    private List<Shop> getCurrentShopkeeperShops() {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null || email.isBlank()) {
            throw new ResourceNotFoundException("Authenticated shopkeeper not found");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Shopkeeper account not found"));
        if (!user.isActive() || user.getRole() != Role.SHOPKEEPER) {
            throw new BadRequestException("An active shopkeeper account is required");
        }
        return shopRepository.findByOwnerId(user.getId());
    }

    private int safeLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            throw new BadRequestException("limit must be at least 1");
        }
        return Math.min(requestedLimit, 50);
    }

    private BigDecimal money(double value) {
        return BigDecimal.valueOf(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class ShopAccumulator {
        private final Long shopId;
        private final String shopName;
        private long totalOrders;
        private long deliveredOrders;
        private long cancelledOrders;
        private BigDecimal value = BigDecimal.ZERO;

        private ShopAccumulator(Long shopId, String shopName) {
            this.shopId = shopId;
            this.shopName = shopName;
        }
    }
}
