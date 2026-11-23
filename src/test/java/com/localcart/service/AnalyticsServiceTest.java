package com.localcart.service;

import com.localcart.exception.BadRequestException;
import com.localcart.repository.OrderItemRepository;
import com.localcart.repository.OrderStatusHistoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AnalyticsServiceTest {

    private OrderStatusHistoryRepository historyRepository;
    private OrderItemRepository orderItemRepository;
    private ShopRepository shopRepository;
    private UserRepository userRepository;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        historyRepository = mock(OrderStatusHistoryRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        shopRepository = mock(ShopRepository.class);
        userRepository = mock(UserRepository.class);
        service = new AnalyticsService(
                historyRepository, orderItemRepository, shopRepository, userRepository
        );
    }

    @Test
    void dailyAnalyticsReturnsOneEntryForEachRequestedDate() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate to = LocalDate.now();
        when(historyRepository.findPlacementEvents(
                any(), any(), any()
        )).thenReturn(List.of());

        var result = service.getAdminDaily(from, to);

        assertEquals(3, result.size());
        assertEquals(from, result.get(0).date());
        assertEquals(to, result.get(2).date());
        assertEquals(0, result.get(0).totalOrders());
        verify(historyRepository).findPlacementEvents(any(), any(), any());
    }

    @Test
    void analyticsRejectsStartDateAfterEndDate() {
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now().minusDays(2);

        assertThrows(BadRequestException.class,
                () -> service.getAdminDaily(from, to));
        verifyNoInteractions(historyRepository);
    }

    @Test
    void analyticsRejectsFutureEndDate() {
        LocalDate future = LocalDate.now().plusDays(1);

        assertThrows(BadRequestException.class,
                () -> service.getAdminDaily(LocalDate.now(), future));
        verifyNoInteractions(historyRepository);
    }

    @Test
    void analyticsRejectsDateRangesLongerThan366Days() {
        LocalDate from = LocalDate.now().minusDays(366);

        assertThrows(BadRequestException.class,
                () -> service.getAdminDaily(from, LocalDate.now()));
        verifyNoInteractions(historyRepository);
    }

    @Test
    void topShopsRejectsNonPositiveLimit() {
        when(historyRepository.findPlacementEvents(any(), any(), any()))
                .thenReturn(List.of());

        assertThrows(BadRequestException.class,
                () -> service.getAdminTopShops(
                        LocalDate.now().minusDays(2), LocalDate.now(), 0));
    }

    @Test
    void topProductsReturnsEmptyWhenThereAreNoPlacedOrders() {
        when(historyRepository.findPlacementEvents(any(), any(), any()))
                .thenReturn(List.of());

        var result = service.getAdminTopProducts(
                LocalDate.now().minusDays(2), LocalDate.now(), 10);

        assertTrue(result.isEmpty());
        verifyNoInteractions(orderItemRepository);
    }
}
