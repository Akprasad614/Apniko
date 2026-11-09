package com.localcart.service;

import com.localcart.exception.BadRequestException;
import com.localcart.repository.OrderStatusHistoryRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopkeeperAiInsightsServiceTest {

    private OrderStatusHistoryRepository historyRepository;
    private ShopInventoryRepository inventoryRepository;
    private ShopRepository shopRepository;
    private UserRepository userRepository;
    private ShopkeeperAiInsightsService service;

    @BeforeEach
    void setUp() {
        historyRepository = mock(OrderStatusHistoryRepository.class);
        inventoryRepository = mock(ShopInventoryRepository.class);
        shopRepository = mock(ShopRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ShopkeeperAiInsightsService(
                historyRepository, inventoryRepository, shopRepository, userRepository
        );
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
}
