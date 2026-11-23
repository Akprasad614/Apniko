package com.localcart.service;

import com.localcart.dto.AiInsightSummaryResponse;
import com.localcart.dto.DailyDemandForecastResponse;
import com.localcart.dto.DemandForecastResponse;
import com.localcart.dto.ReplenishmentRecommendationResponse;
import com.localcart.entity.Order;
import com.localcart.entity.OrderItem;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.OrderStatusHistory;
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
import com.localcart.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explainable baseline demand forecasting for a shopkeeper's own inventory.
 * It uses a weighted moving average with a capped trend adjustment; it is not
 * a trained machine-learning model and does not call an external AI provider.
 */
@Service
@Transactional(readOnly = true)
public class ShopkeeperAiInsightsService {

    public static final String FORECASTING_METHOD = "WEIGHTED_MOVING_AVERAGE_WITH_CAPPED_TREND";
    private static final int DEFAULT_HISTORY_DAYS = 28;
    private static final int DEFAULT_FORECAST_DAYS = 7;
    private static final int DEFAULT_LEAD_TIME_DAYS = 3;
    private static final int REVIEW_PERIOD_DAYS = 7;

    private final OrderStatusHistoryRepository historyRepository;
    private final ShopInventoryRepository inventoryRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public ShopkeeperAiInsightsService(
            OrderStatusHistoryRepository historyRepository,
            ShopInventoryRepository inventoryRepository,
            ShopRepository shopRepository,
            UserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.inventoryRepository = inventoryRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    public DemandForecastResponse getProductForecast(
            Long shopId, Long productId, Integer requestedForecastDays, Integer requestedHistoryDays) {
        int forecastDays = validateRange(requestedForecastDays, DEFAULT_FORECAST_DAYS, 1, 30,
                "forecastDays must be between 1 and 30");
        int historyDays = validateRange(requestedHistoryDays, DEFAULT_HISTORY_DAYS, 7, 90,
                "historyDays must be between 7 and 90");

        Shop shop = getOwnedShop(shopId);
        ShopInventory inventory = inventoryRepository.findByShopIdAndProductId(shopId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product is not present in this shop's inventory"));

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(historyDays - 1L);
        DemandData data = loadDemand(shop, from, today, historyDays);
        int[] dailyUnits = data.unitsByProduct().getOrDefault(productId, new int[historyDays]);
        ForecastValues values = forecast(dailyUnits);

        List<DailyDemandForecastResponse> daily = new ArrayList<>();
        double total = 0.0;
        for (int day = 1; day <= forecastDays; day++) {
            double expected = round(values.predictedDailyUnits());
            total += expected;
            daily.add(new DailyDemandForecastResponse(today.plusDays(day), expected));
        }

        return new DemandForecastResponse(
                shop.getId(), shop.getName(), inventory.getProduct().getId(),
                inventory.getProduct().getName(), from, today, historyDays,
                values.activeSalesDays(), forecastDays,
                round(values.averageDailyUnits()), round(values.predictedDailyUnits()),
                round(total), values.confidence(), FORECASTING_METHOD,
                LocalDateTime.now(), List.copyOf(daily)
        );
    }

    public List<ReplenishmentRecommendationResponse> getReplenishmentRecommendations(
            Long shopId, Integer requestedHistoryDays, Integer requestedLeadTimeDays) {
        int historyDays = validateRange(requestedHistoryDays, DEFAULT_HISTORY_DAYS, 7, 90,
                "historyDays must be between 7 and 90");
        int leadTimeDays = validateRange(requestedLeadTimeDays, DEFAULT_LEAD_TIME_DAYS, 1, 30,
                "leadTimeDays must be between 1 and 30");

        Shop shop = getOwnedShop(shopId);
        List<ShopInventory> inventory = inventoryRepository.findByShopId(shopId);
        if (inventory.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(historyDays - 1L);
        DemandData data = loadDemand(shop, from, today, historyDays);
        List<ReplenishmentRecommendationResponse> recommendations = new ArrayList<>();

        for (ShopInventory item : inventory) {
            Long productId = item.getProduct().getId();
            int[] dailyUnits = data.unitsByProduct().getOrDefault(productId, new int[historyDays]);
            ForecastValues values = forecast(dailyUnits);
            recommendations.add(buildRecommendation(shop, item, values, leadTimeDays));
        }

        recommendations.sort(Comparator
                .comparingInt((ReplenishmentRecommendationResponse r) -> priority(r.status()))
                .thenComparing(ReplenishmentRecommendationResponse::suggestedReorderQuantity,
                        Comparator.reverseOrder())
                .thenComparing(ReplenishmentRecommendationResponse::productName,
                        String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(recommendations);
    }

    public AiInsightSummaryResponse getSummary(Long shopId, Integer requestedHistoryDays) {
        int historyDays = validateRange(requestedHistoryDays, DEFAULT_HISTORY_DAYS, 7, 90,
                "historyDays must be between 7 and 90");
        Shop shop = getOwnedShop(shopId);
        List<ReplenishmentRecommendationResponse> recommendations =
                getReplenishmentRecommendations(shopId, historyDays, DEFAULT_LEAD_TIME_DAYS);

        double nextSevenDays = recommendations.stream()
                .mapToDouble(item -> item.predictedDailyDemand() * 7.0)
                .sum();
        int attention = (int) recommendations.stream()
                .filter(item -> !"HEALTHY".equals(item.status()))
                .count();

        return new AiInsightSummaryResponse(
                shop.getId(), shop.getName(), LocalDateTime.now(), historyDays,
                recommendations.size(), attention, round(nextSevenDays),
                FORECASTING_METHOD, recommendations
        );
    }

    private DemandData loadDemand(Shop shop, LocalDate from, LocalDate to, int historyDays) {
        List<OrderStatusHistory> events = historyRepository.findPlacementEventsForShops(
                List.of(shop.getId()), OrderStatus.PLACED,
                from.atStartOfDay(), to.plusDays(1).atStartOfDay()
        );

        Map<Long, int[]> unitsByProduct = new HashMap<>();
        Set<Long> processedOrderIds = new HashSet<>();
        int eventCount = 0;

        for (OrderStatusHistory event : events) {
            Order order = event.getOrder();
            if (order == null || order.getId() == null || !processedOrderIds.add(order.getId())) {
                continue;
            }
            if (order.getStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            int dayIndex = (int) java.time.temporal.ChronoUnit.DAYS
                    .between(from, event.getChangedAt().toLocalDate());
            if (dayIndex < 0 || dayIndex >= historyDays) {
                continue;
            }
            eventCount++;
            if (order.getItems() == null) {
                continue;
            }
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() == null || item.getProduct().getId() == null || item.getQuantity() <= 0) {
                    continue;
                }
                int[] series = unitsByProduct.computeIfAbsent(
                        item.getProduct().getId(), ignored -> new int[historyDays]);
                series[dayIndex] += item.getQuantity();
            }
        }
        return new DemandData(unitsByProduct, eventCount);
    }

    private ForecastValues forecast(int[] dailyUnits) {
        int n = dailyUnits.length;
        int lastStart = Math.max(0, n - 7);
        int previousStart = Math.max(0, n - 14);
        int earlierStart = Math.max(0, n - 28);

        double lastSeven = mean(dailyUnits, lastStart, n);
        double previousSeven = n > 7 ? mean(dailyUnits, previousStart, lastStart) : lastSeven;
        double earlierFourteen = n > 14 ? mean(dailyUnits, earlierStart, previousStart) : previousSeven;

        double weightedTotal = 0.60 * lastSeven + 0.25 * previousSeven + 0.15 * earlierFourteen;
        double trendChange = previousSeven <= 0.0 ? 0.0 : (lastSeven - previousSeven) / previousSeven;
        double cappedTrend = Math.max(-0.30, Math.min(0.30, trendChange));
        double predictedDaily = Math.max(0.0, weightedTotal * (1.0 + (0.35 * cappedTrend)));
        double average = mean(dailyUnits, 0, n);

        int activeDays = 0;
        for (int units : dailyUnits) {
            if (units > 0) activeDays++;
        }
        String confidence = n >= 28 && activeDays >= 14 ? "HIGH"
                : n >= 14 && activeDays >= 5 ? "MEDIUM" : "LOW";
        return new ForecastValues(average, predictedDaily, activeDays, confidence, populationStdDev(dailyUnits));
    }

    private ReplenishmentRecommendationResponse buildRecommendation(
            Shop shop, ShopInventory inventory, ForecastValues values, int leadTimeDays) {
        int currentStock = Math.max(0, inventory.getStockQuantity());
        int threshold = inventory.getLowStockThreshold() == null
                ? 5 : Math.max(0, inventory.getLowStockThreshold());
        int expectedLeadDemand = (int) Math.ceil(values.predictedDailyUnits() * leadTimeDays);
        int safetyStock = (int) Math.ceil(values.dailyStdDev() * Math.sqrt(leadTimeDays) * 1.28);
        int targetStock = (int) Math.ceil(values.predictedDailyUnits()
                * (leadTimeDays + REVIEW_PERIOD_DAYS)) + safetyStock;
        int suggestedQuantity = Math.max(0, targetStock - currentStock);
        Double daysOfCover = values.predictedDailyUnits() <= 0.0
                ? null : round(currentStock / values.predictedDailyUnits());

        String status;
        String explanation;
        if (currentStock <= threshold) {
            status = "REORDER_NOW";
            explanation = "Current stock is at or below the shop's low-stock threshold.";
        } else if (daysOfCover != null && daysOfCover <= leadTimeDays) {
            status = "REORDER_NOW";
            explanation = "Estimated stock cover is shorter than or equal to supplier lead time.";
        } else if (daysOfCover != null && daysOfCover <= leadTimeDays + 3.0) {
            status = "REORDER_SOON";
            explanation = "Estimated stock cover is approaching supplier lead time.";
        } else {
            status = "HEALTHY";
            explanation = values.activeSalesDays() < 4
                    ? "Limited sales history; treat this estimate as low confidence."
                    : "Current stock appears sufficient for the estimated demand and lead time.";
        }

        return new ReplenishmentRecommendationResponse(
                shop.getId(), shop.getName(), inventory.getId(), inventory.getProduct().getId(),
                inventory.getProduct().getName(), currentStock, threshold,
                round(values.predictedDailyUnits()), leadTimeDays, expectedLeadDemand,
                safetyStock, suggestedQuantity, daysOfCover, status, explanation
        );
    }

    private Shop getOwnedShop(Long shopId) {
        if (shopId == null) {
            throw new BadRequestException("shopId is required");
        }
        String email = SecurityUtils.getCurrentUserEmail();
        User user = userRepository.findByEmail(email == null ? "" : email)
                .filter(User::isActive)
                .filter(candidate -> candidate.getRole() == Role.SHOPKEEPER)
                .orElseThrow(() -> new ResourceNotFoundException("Active shopkeeper account not found"));
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        if (shop.getOwner() == null || !shop.getOwner().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Shop not found in your shopkeeper account");
        }
        return shop;
    }

    private int validateRange(Integer requested, int defaultValue, int min, int max, String message) {
        int value = requested == null ? defaultValue : requested;
        if (value < min || value > max) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private double mean(int[] values, int startInclusive, int endExclusive) {
        if (startInclusive >= endExclusive) return 0.0;
        long total = 0;
        for (int i = startInclusive; i < endExclusive; i++) total += values[i];
        return (double) total / (endExclusive - startInclusive);
    }

    private double populationStdDev(int[] values) {
        if (values.length == 0) return 0.0;
        double avg = mean(values, 0, values.length);
        double variance = 0.0;
        for (int value : values) {
            double difference = value - avg;
            variance += difference * difference;
        }
        return Math.sqrt(variance / values.length);
    }

    private int priority(String status) {
        return switch (status) {
            case "REORDER_NOW" -> 0;
            case "REORDER_SOON" -> 1;
            default -> 2;
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record DemandData(Map<Long, int[]> unitsByProduct, int eventCount) { }

    private record ForecastValues(
            double averageDailyUnits,
            double predictedDailyUnits,
            int activeSalesDays,
            String confidence,
            double dailyStdDev
    ) { }
}
