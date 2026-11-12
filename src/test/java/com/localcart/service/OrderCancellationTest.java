package com.localcart.service;

import com.localcart.entity.DeliveryMode;
import com.localcart.entity.InventoryMovement;
import com.localcart.entity.Order;
import com.localcart.entity.OrderItem;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Payment;
import com.localcart.entity.Product;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderCancellationTest {

    private OrderRepository orderRepository;
    private OrderStatusHistoryRepository historyRepository;
    private PaymentRepository paymentRepository;
    private InventoryMovementRepository movementRepository;
    private OrderItemRepository orderItemRepository;
    private ShopRepository shopRepository;
    private ShopInventoryRepository inventoryRepository;
    private UserRepository userRepository;
    private CartRepository cartRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        historyRepository = mock(OrderStatusHistoryRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        movementRepository = mock(InventoryMovementRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        shopRepository = mock(ShopRepository.class);
        inventoryRepository = mock(ShopInventoryRepository.class);
        userRepository = mock(UserRepository.class);
        cartRepository = mock(CartRepository.class);
        CustomerAddressRepository customerAddressRepository = mock(CustomerAddressRepository.class);
        orderService = new OrderService(
                orderRepository,
                historyRepository,
                paymentRepository,
                movementRepository,
                orderItemRepository,
                shopRepository,
                inventoryRepository,
                userRepository,
                cartRepository,
                customerAddressRepository
        );
    }

    @Test
    void customerCanCancelPlacedOrderAndStockIsRestored() {
        User customer = user(10L, "customer@example.com", Role.CUSTOMER);
        Shop shop = shop(1L);
        Product product = product(101L, "Rice");
        Order order = order(501L, customer, shop, product, OrderStatus.PLACED, 2);
        ShopInventory inventory = inventory(shop, product, 5);
        arrangeCustomerAndOrder(customer, order, inventory);
        when(orderRepository.save(order)).thenReturn(order);
        when(paymentRepository.findByOrderId(501L)).thenReturn(Optional.empty());

        Optional<Order> result = orderService.cancelOrderByCustomer(
                501L, "customer@example.com", "Changed my mind");

        assertTrue(result.isPresent());
        assertEquals(OrderStatus.CANCELLED, result.get().getStatus());
        assertEquals(7, inventory.getStockQuantity());
        assertTrue(inventory.isAvailable());
        verify(historyRepository).save(any());
        verify(movementRepository).save(argThat(movement ->
                movement.getPreviousQuantity() == 5
                        && movement.getNewQuantity() == 7
                        && movement.getQuantityChange() == 2
        ));
        verify(orderRepository).save(order);
    }

    @Test
    void customerCannotCancelAnotherCustomersOrder() {
        User currentCustomer = user(10L, "customer@example.com", Role.CUSTOMER);
        User orderCustomer = user(11L, "other@example.com", Role.CUSTOMER);
        Order order = order(502L, orderCustomer, shop(1L), product(101L, "Rice"),
                OrderStatus.PLACED, 1);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(currentCustomer));
        when(orderRepository.findByIdForUpdate(502L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class,
                () -> orderService.cancelOrderByCustomer(502L, "customer@example.com", "Cancel"));

        verify(inventoryRepository, never()).findByShopIdAndProductIdForUpdate(anyLong(), anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void customerCannotCancelOrderAfterItHasBeenAccepted() {
        User customer = user(10L, "customer@example.com", Role.CUSTOMER);
        Order order = order(503L, customer, shop(1L), product(101L, "Rice"),
                OrderStatus.ACCEPTED, 1);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(503L)).thenReturn(Optional.of(order));

        Optional<Order> result = orderService.cancelOrderByCustomer(
                503L, "customer@example.com", "Too late");

        assertTrue(result.isEmpty());
        verify(inventoryRepository, never()).findByShopIdAndProductIdForUpdate(anyLong(), anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    private void arrangeCustomerAndOrder(User customer, Order order, ShopInventory inventory) {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inventoryRepository.findByShopIdAndProductIdForUpdate(1L, 101L))
                .thenReturn(Optional.of(inventory));
    }

    private User user(Long id, String email, Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.getName()).thenReturn("Test " + role.name());
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(true);
        return user;
    }

    private Shop shop(Long id) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(id);
        return shop;
    }

    private Product product(Long id, String name) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn(name);
        return product;
    }

    private Order order(Long id, User customer, Shop shop, Product product,
                        OrderStatus status, int quantity) {
        Order order = new Order();
        order.setId(id);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(status);
        order.setDeliveryMode(DeliveryMode.SHOP_DELIVERY);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        order.getItems().add(item);
        return order;
    }

    private ShopInventory inventory(Shop shop, Product product, int stock) {
        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setStockQuantity(stock);
        inventory.setAvailable(stock > 0);
        return inventory;
    }
}
