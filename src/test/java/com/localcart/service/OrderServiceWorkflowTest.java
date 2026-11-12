package com.localcart.service;

import com.localcart.entity.DeliveryMode;
import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceWorkflowTest {

    private OrderRepository orderRepository;
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    private PaymentRepository paymentRepository;
    private InventoryMovementRepository inventoryMovementRepository;
    private OrderItemRepository orderItemRepository;
    private ShopRepository shopRepository;
    private ShopInventoryRepository shopInventoryRepository;
    private UserRepository userRepository;
    private CartRepository cartRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderStatusHistoryRepository = mock(OrderStatusHistoryRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        inventoryMovementRepository = mock(InventoryMovementRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        shopRepository = mock(ShopRepository.class);
        shopInventoryRepository = mock(ShopInventoryRepository.class);
        userRepository = mock(UserRepository.class);
        cartRepository = mock(CartRepository.class);
        CustomerAddressRepository customerAddressRepository = mock(CustomerAddressRepository.class);

        orderService = new OrderService(
                orderRepository,
                orderStatusHistoryRepository,
                paymentRepository,
                inventoryMovementRepository,
                orderItemRepository,
                shopRepository,
                shopInventoryRepository,
                userRepository,
                cartRepository,
                customerAddressRepository
        );
    }

    @Test
    void shopkeeperCanMovePlacedOrderToAccepted() {
        User shopkeeper = user(Role.SHOPKEEPER, 12L, "shopkeeper@example.com");
        Order order = order(OrderStatus.PLACED, DeliveryMode.SHOP_DELIVERY);
        order.setShop(shopOwnedBy(shopkeeper));

        when(userRepository.findByEmail(shopkeeper.getEmail()))
                .thenReturn(Optional.of(shopkeeper));
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Optional<Order> result = orderService.updateShopkeeperOrderStatus(
                100L, OrderStatus.ACCEPTED, shopkeeper.getEmail()
        );

        assertTrue(result.isPresent());
        assertEquals(OrderStatus.ACCEPTED, result.get().getStatus());
        verify(orderStatusHistoryRepository).save(any());
        verify(orderRepository).save(order);
    }

    @Test
    void shopkeeperCannotSkipOrderLifecycleSteps() {
        User shopkeeper = user(Role.SHOPKEEPER, 12L, "shopkeeper@example.com");
        Order order = order(OrderStatus.PLACED, DeliveryMode.SHOP_DELIVERY);
        order.setShop(shopOwnedBy(shopkeeper));

        when(userRepository.findByEmail(shopkeeper.getEmail()))
                .thenReturn(Optional.of(shopkeeper));
        when(orderRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(order));

        Optional<Order> result = orderService.updateShopkeeperOrderStatus(
                101L, OrderStatus.READY, shopkeeper.getEmail()
        );

        assertTrue(result.isEmpty());
        assertEquals(OrderStatus.PLACED, order.getStatus());
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shopkeeperCannotUpdateAnOrderOwnedByAnotherShopkeeper() {
        User currentShopkeeper = user(Role.SHOPKEEPER, 12L, "shopkeeper@example.com");
        User differentOwner = user(Role.SHOPKEEPER, 99L, "other@example.com");
        Order order = order(OrderStatus.PLACED, DeliveryMode.SHOP_DELIVERY);
        order.setShop(shopOwnedBy(differentOwner));

        when(userRepository.findByEmail(currentShopkeeper.getEmail()))
                .thenReturn(Optional.of(currentShopkeeper));
        when(orderRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                orderService.updateShopkeeperOrderStatus(
                        102L, OrderStatus.ACCEPTED, currentShopkeeper.getEmail()
                )
        );

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void availableDeliveryQueueContainsOnlyReadyPlatformDeliveryOrders() {
        Order platformOrder = order(OrderStatus.READY, DeliveryMode.PLATFORM_DELIVERY);
        Order shopOrder = order(OrderStatus.READY, DeliveryMode.SHOP_DELIVERY);
        Order pickupOrder = order(OrderStatus.READY, DeliveryMode.CUSTOMER_PICKUP);
        Order notReadyOrder = order(OrderStatus.PREPARING, DeliveryMode.PLATFORM_DELIVERY);

        when(orderRepository.findByStatusAndDeliveryPartnerIsNull(OrderStatus.READY))
                .thenReturn(List.of(platformOrder, shopOrder, pickupOrder));

        List<Order> available = orderService.getAvailableDeliveryOrders();

        assertEquals(1, available.size());
        assertSame(platformOrder, available.get(0));
        assertFalse(available.contains(notReadyOrder));
    }

    @Test
    void deliveryPartnerCanAcceptReadyPlatformOrder() {
        User partner = user(Role.DELIVERY_PARTNER, 15L, "partner@example.com");
        Order order = order(OrderStatus.READY, DeliveryMode.PLATFORM_DELIVERY);

        when(userRepository.findByEmail(partner.getEmail())).thenReturn(Optional.of(partner));
        when(orderRepository.findByIdForUpdate(103L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order accepted = orderService.acceptDeliveryOrder(103L, partner.getEmail());

        assertSame(partner, accepted.getDeliveryPartner());
        assertEquals(OrderStatus.READY, accepted.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void deliveryPartnerCannotAcceptShopDeliveryOrder() {
        User partner = user(Role.DELIVERY_PARTNER, 15L, "partner@example.com");
        Order order = order(OrderStatus.READY, DeliveryMode.SHOP_DELIVERY);

        when(userRepository.findByEmail(partner.getEmail())).thenReturn(Optional.of(partner));
        when(orderRepository.findByIdForUpdate(104L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                orderService.acceptDeliveryOrder(104L, partner.getEmail())
        );
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void assignedDeliveryPartnerCanPickUpReadyOrder() {
        User partner = user(Role.DELIVERY_PARTNER, 15L, "partner@example.com");
        Order order = order(OrderStatus.READY, DeliveryMode.PLATFORM_DELIVERY);
        order.setDeliveryPartner(partner);

        when(userRepository.findByEmail(partner.getEmail())).thenReturn(Optional.of(partner));
        when(orderRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order pickedUp = orderService.pickupOrder(105L, partner.getEmail());

        assertEquals(OrderStatus.OUT_FOR_DELIVERY, pickedUp.getStatus());
        verify(orderStatusHistoryRepository).save(any());
        verify(orderRepository).save(order);
    }

    @Test
    void assignedDeliveryPartnerCanCompleteDelivery() {
        User partner = user(Role.DELIVERY_PARTNER, 15L, "partner@example.com");
        Order order = order(OrderStatus.OUT_FOR_DELIVERY, DeliveryMode.PLATFORM_DELIVERY);
        order.setDeliveryPartner(partner);

        when(userRepository.findByEmail(partner.getEmail())).thenReturn(Optional.of(partner));
        when(orderRepository.findByIdForUpdate(106L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order delivered = orderService.deliverOrder(106L, partner.getEmail());

        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());
        verify(orderStatusHistoryRepository).save(any());
        verify(orderRepository).save(order);
    }

    @Test
    void deliveryPartnerCannotPickUpAnotherPartnersOrder() {
        User currentPartner = user(Role.DELIVERY_PARTNER, 15L, "partner@example.com");
        User assignedPartner = user(Role.DELIVERY_PARTNER, 16L, "otherpartner@example.com");
        Order order = order(OrderStatus.READY, DeliveryMode.PLATFORM_DELIVERY);
        order.setDeliveryPartner(assignedPartner);

        when(userRepository.findByEmail(currentPartner.getEmail()))
                .thenReturn(Optional.of(currentPartner));
        when(orderRepository.findByIdForUpdate(107L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                orderService.pickupOrder(107L, currentPartner.getEmail())
        );
        verify(orderRepository, never()).save(any(Order.class));
    }

    private User user(Role role, Long id, String email) {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.getName()).thenReturn("Test " + role.name());
        when(user.isActive()).thenReturn(true);
        return user;
    }

    private Shop shopOwnedBy(User owner) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(1L);
        when(shop.getOwner()).thenReturn(owner);
        return shop;
    }

    private Order order(OrderStatus status, DeliveryMode deliveryMode) {
        Order order = new Order();
        order.setStatus(status);
        order.setDeliveryMode(deliveryMode);
        return order;
    }
}
