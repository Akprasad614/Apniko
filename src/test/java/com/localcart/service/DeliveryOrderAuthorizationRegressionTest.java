package com.localcart.service;

import com.localcart.entity.DeliveryMode;
import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.CartRepository;
import com.localcart.repository.CustomerAddressRepository;
import com.localcart.repository.InventoryMovementRepository;
import com.localcart.repository.OrderItemRepository;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.OrderStatusHistoryRepository;
import com.localcart.repository.PaymentRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class DeliveryOrderAuthorizationRegressionTest {

    private OrderRepository orderRepository;
    private OrderStatusHistoryRepository historyRepository;
    private UserRepository userRepository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        historyRepository = mock(OrderStatusHistoryRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        InventoryMovementRepository movementRepository = mock(InventoryMovementRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        ShopRepository shopRepository = mock(ShopRepository.class);
        ShopInventoryRepository inventoryRepository = mock(ShopInventoryRepository.class);
        userRepository = mock(UserRepository.class);
        CartRepository cartRepository = mock(CartRepository.class);
        CustomerAddressRepository customerAddressRepository = mock(CustomerAddressRepository.class);
        service = new OrderService(
                orderRepository, historyRepository, paymentRepository, movementRepository,
                orderItemRepository, shopRepository, inventoryRepository, userRepository, cartRepository,
                customerAddressRepository
        );
    }

    @Test
    void deliveryPartnerCannotDeliverAnOrderAssignedToSomeoneElse() {
        User caller = user(15L, Role.DELIVERY_PARTNER, true, "partner@example.com");
        User assignedPartner = user(16L, Role.DELIVERY_PARTNER, true, "other@example.com");
        Order order = order(OrderStatus.OUT_FOR_DELIVERY);
        order.setDeliveryPartner(assignedPartner);
        when(userRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(orderRepository.findByIdForUpdate(107L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                service.deliverOrder(107L, caller.getEmail())
        );

        verify(orderRepository, never()).save(any(Order.class));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void deliveryPartnerCannotTakeAnOrderAlreadyAssignedToAnotherPartner() {
        User caller = user(15L, Role.DELIVERY_PARTNER, true, "partner@example.com");
        User assignedPartner = user(16L, Role.DELIVERY_PARTNER, true, "other@example.com");
        Order order = order(OrderStatus.READY);
        order.setDeliveryPartner(assignedPartner);
        when(userRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(orderRepository.findByIdForUpdate(108L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                service.acceptDeliveryOrder(108L, caller.getEmail())
        );

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shopkeeperCannotUseDeliveryPartnerOrderListingService() {
        User shopkeeper = user(12L, Role.SHOPKEEPER, true, "shopkeeper@example.com");
        when(userRepository.findByEmail(shopkeeper.getEmail())).thenReturn(Optional.of(shopkeeper));

        assertThrows(BadRequestException.class, () -> service.getMyDeliveryOrders(shopkeeper.getEmail()));

        verify(orderRepository, never()).findByDeliveryPartnerId(anyLong());
    }

    @Test
    void inactiveDeliveryPartnerCannotAccessAssignedOrderList() {
        User partner = user(15L, Role.DELIVERY_PARTNER, false, "inactive@example.com");
        when(userRepository.findByEmail(partner.getEmail())).thenReturn(Optional.of(partner));

        assertThrows(BadRequestException.class, () -> service.getMyDeliveryOrders(partner.getEmail()));

        verify(orderRepository, never()).findByDeliveryPartnerId(anyLong());
    }

    private User user(Long id, Role role, boolean active, String email) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(active);
        when(user.getEmail()).thenReturn(email);
        return user;
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setStatus(status);
        order.setDeliveryMode(DeliveryMode.PLATFORM_DELIVERY);
        return order;
    }
}
