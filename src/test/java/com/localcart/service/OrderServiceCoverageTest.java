package com.localcart.service;

import com.localcart.dto.CreateOrderRequest;
import com.localcart.dto.OrderItemRequest;
import com.localcart.entity.Cart;
import com.localcart.entity.DeliveryMode;
import com.localcart.entity.Order;
import com.localcart.entity.OrderItem;
import com.localcart.entity.Product;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.CartRepository;
import com.localcart.repository.InventoryMovementRepository;
import com.localcart.repository.OrderItemRepository;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.OrderStatusHistoryRepository;
import com.localcart.repository.PaymentRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceCoverageTest {

    private OrderRepository orderRepository;
    private OrderStatusHistoryRepository historyRepository;
    private PaymentRepository paymentRepository;
    private InventoryMovementRepository movementRepository;
    private OrderItemRepository orderItemRepository;
    private ShopRepository shopRepository;
    private ShopInventoryRepository inventoryRepository;
    private UserRepository userRepository;
    private CartRepository cartRepository;
    private OrderService service;
    private MockedStatic<SecurityUtils> securityUtils;

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
        service = new OrderService(orderRepository, historyRepository, paymentRepository,
                movementRepository, orderItemRepository, shopRepository,
                inventoryRepository, userRepository, cartRepository);
        securityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void createOrderReturnsEmptyWhenShopDoesNotExist() {
        CreateOrderRequest request = createRequest(1L);
        when(shopRepository.findById(1L)).thenReturn(Optional.empty());

        assertTrue(service.createOrder(request).isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void createOrderReturnsEmptyWhenAuthenticationIsMissing() {
        CreateOrderRequest request = createRequest(1L);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop(1L, true)));
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);

        assertTrue(service.createOrder(request).isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void createOrderReturnsEmptyWhenCustomerAccountIsMissing() {
        CreateOrderRequest request = createRequest(1L);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop(1L, true)));
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertTrue(service.createOrder(request).isEmpty());
    }

    @Test
    void createOrderReturnsEmptyWhenShopIsInactive() {
        CreateOrderRequest request = createRequest(1L);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop(1L, false)));
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(10L, Role.CUSTOMER, true)));

        assertTrue(service.createOrder(request).isEmpty());
        verify(userRepository).findByEmail("customer@example.com");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrderReturnsEmptyWhenAuthenticatedUserIsNotCustomer() {
        CreateOrderRequest request = createRequest(1L);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop(1L, true)));
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(user(7L, Role.SHOPKEEPER, true)));

        assertTrue(service.createOrder(request).isEmpty());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void customerCanCreateOrderAndStockIsDeducted() {
        User customer = user(10L, Role.CUSTOMER, true);
        Shop shop = shop(1L, true);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(101L);
        when(product.getName()).thenReturn("Rice");
        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setPrice(40.0);
        inventory.setStockQuantity(5);
        inventory.setAvailable(true);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByShopIdAndProductIdForUpdate(1L, 101L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(orderItemRepository.save(any(OrderItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShopId(1L);
        request.setDeliveryMode(DeliveryMode.SHOP_DELIVERY);
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(101L);
        item.setQuantity(2);
        request.setItems(List.of(item));

        Optional<Order> result = service.createOrder(request);

        assertTrue(result.isPresent());
        assertEquals(OrderStatus.PLACED, result.get().getStatus());
        assertEquals(80.0, result.get().getTotalAmount());
        assertEquals(3, inventory.getStockQuantity());
        assertTrue(inventory.isAvailable());
        verify(historyRepository).save(any());
        verify(movementRepository).save(any());
        verify(orderItemRepository).save(any(OrderItem.class));
    }

    @Test
    void orderCreationRejectsInsufficientStockWithoutDeductingInventory() {
        User customer = user(10L, Role.CUSTOMER, true);
        Shop shop = shop(1L, true);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(101L);
        when(product.getName()).thenReturn("Rice");
        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setPrice(40.0);
        inventory.setStockQuantity(1);
        inventory.setAvailable(true);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByShopIdAndProductIdForUpdate(1L, 101L))
                .thenReturn(Optional.of(inventory));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShopId(1L);
        request.setDeliveryMode(DeliveryMode.SHOP_DELIVERY);
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(101L);
        item.setQuantity(2);
        request.setItems(List.of(item));

        assertThrows(BadRequestException.class, () -> service.createOrder(request));
        assertEquals(1, inventory.getStockQuantity());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
        verify(inventoryRepository, never()).save(any(ShopInventory.class));
    }

    @Test
    void orderCreationRejectsProductMissingFromSelectedShop() {
        User customer = user(10L, Role.CUSTOMER, true);
        Shop shop = shop(1L, true);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByShopIdAndProductIdForUpdate(1L, 101L))
                .thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShopId(1L);
        request.setDeliveryMode(DeliveryMode.SHOP_DELIVERY);
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(101L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        assertThrows(BadRequestException.class, () -> service.createOrder(request));
        verify(inventoryRepository, never()).save(any(ShopInventory.class));
    }

    @Test
    void checkoutRejectsInactiveShopBeforeReadingCart() {
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(10L, Role.CUSTOMER, true)));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop(1L, false)));

        assertThrows(BadRequestException.class,
                () -> service.checkoutCart("customer@example.com", 1L, DeliveryMode.SHOP_DELIVERY));
        verifyNoInteractions(cartRepository);
    }

    @Test
    void checkoutRejectsEmptyCart() {
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(10L, Role.CUSTOMER, true)));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop(1L, true)));
        when(cartRepository.findByCustomerIdAndShopId(10L, 1L))
                .thenReturn(Optional.of(new Cart()));

        assertThrows(BadRequestException.class,
                () -> service.checkoutCart("customer@example.com", 1L, DeliveryMode.SHOP_DELIVERY));
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void shopAndCustomerOrderLookupsReturnEmptyForUnknownRecords() {
        when(shopRepository.existsById(99L)).thenReturn(false);
        when(userRepository.existsById(88L)).thenReturn(false);

        assertTrue(service.getOrdersByShop(99L).isEmpty());
        assertTrue(service.getOrdersByCustomer(88L).isEmpty());
        verify(orderRepository, never()).findByShopId(anyLong());
        verify(orderRepository, never()).findByCustomerId(anyLong());
    }

    @Test
    void shopkeeperStatusChangeRejectsNullOrSkippedTransition() {
        User owner = user(12L, Role.SHOPKEEPER, true);
        Order order = new Order();
        order.setStatus(OrderStatus.PREPARING);
        order.setShop(shopOwnedBy(owner));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));

        assertTrue(service.updateShopkeeperOrderStatus(
                100L, OrderStatus.ACCEPTED, "owner@example.com").isEmpty());
        assertTrue(service.updateShopkeeperOrderStatus(
                100L, null, "owner@example.com").isEmpty());
        verify(historyRepository, never()).save(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void deliveryPartnerCannotAcceptOrderAlreadyAssignedToAnotherPartner() {
        User currentPartner = user(15L, Role.DELIVERY_PARTNER, true);
        User assignedPartner = user(16L, Role.DELIVERY_PARTNER, true);
        Order order = new Order();
        order.setStatus(OrderStatus.READY);
        order.setDeliveryMode(DeliveryMode.PLATFORM_DELIVERY);
        order.setDeliveryPartner(assignedPartner);
        when(userRepository.findByEmail("partner@example.com"))
                .thenReturn(Optional.of(currentPartner));
        when(orderRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class,
                () -> service.acceptDeliveryOrder(101L, "partner@example.com"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void pickupRejectsAnOrderThatHasNoAssignedDeliveryPartner() {
        User partner = user(15L, Role.DELIVERY_PARTNER, true);
        Order order = new Order();
        order.setStatus(OrderStatus.READY);
        order.setDeliveryMode(DeliveryMode.PLATFORM_DELIVERY);
        when(userRepository.findByEmail("partner@example.com")).thenReturn(Optional.of(partner));
        when(orderRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class,
                () -> service.pickupOrder(102L, "partner@example.com"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void deliveryPartnerMustBeActiveAndHaveCorrectRole() {
        when(userRepository.findByEmail("inactive@example.com"))
                .thenReturn(Optional.of(user(15L, Role.DELIVERY_PARTNER, false)));
        assertThrows(BadRequestException.class,
                () -> service.getMyDeliveryOrders("inactive@example.com"));

        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(10L, Role.CUSTOMER, true)));
        assertThrows(BadRequestException.class,
                () -> service.getMyDeliveryOrders("customer@example.com"));
        verify(orderRepository, never()).findByDeliveryPartnerId(anyLong());
    }

    @Test
    void historyLookupReturnsEmptyListWhenNoStatusChangesExist() {
        when(historyRepository.findByOrder_IdOrderByChangedAtAscIdAsc(501L))
                .thenReturn(List.of());

        assertTrue(service.getOrderStatusHistory(501L).isEmpty());
        verify(historyRepository).findByOrder_IdOrderByChangedAtAscIdAsc(501L);
    }

    private static CreateOrderRequest createRequest(Long shopId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setShopId(shopId);
        request.setDeliveryMode(DeliveryMode.SHOP_DELIVERY);
        request.setItems(List.of());
        return request;
    }

    private static User user(Long id, Role role, boolean active) {
        User user = new User();
        user.setName("Test " + role.name());
        user.setEmail(role == Role.SHOPKEEPER
                ? "owner@example.com" : role == Role.DELIVERY_PARTNER
                ? "partner@example.com" : "customer@example.com");
        user.setRole(role);
        user.setActive(active);
        setEntityId(user, id);
        return user;
    }

    private static Shop shop(Long id, boolean active) {
        Shop shop = new Shop();
        shop.setName("Test Shop " + id);
        shop.setActive(active);
        setEntityId(shop, id);
        return shop;
    }

    private static Shop shopOwnedBy(User owner) {
        Shop shop = new Shop();
        shop.setName("Test Shop");
        shop.setActive(true);
        shop.setOwner(owner);
        setEntityId(shop, 1L);
        return shop;
    }

    private static void setEntityId(Object entity, Long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to assign test entity ID", exception);
        }
    }
}
