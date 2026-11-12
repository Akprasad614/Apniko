package com.localcart.service;

import com.localcart.entity.Cart;
import com.localcart.entity.CartItem;
import com.localcart.entity.CustomerAddress;
import com.localcart.entity.DeliveryMode;
import com.localcart.entity.Order;
import com.localcart.entity.Product;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.User;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderCheckoutAddressSnapshotTest {

    private OrderRepository orderRepository;
    private CartRepository cartRepository;
    private CustomerAddressRepository addressRepository;
    private UserRepository userRepository;
    private ShopRepository shopRepository;
    private ShopInventoryRepository inventoryRepository;
    private OrderItemRepository orderItemRepository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        cartRepository = mock(CartRepository.class);
        addressRepository = mock(CustomerAddressRepository.class);
        userRepository = mock(UserRepository.class);
        shopRepository = mock(ShopRepository.class);
        inventoryRepository = mock(ShopInventoryRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);

        service = new OrderService(
                orderRepository,
                mock(OrderStatusHistoryRepository.class),
                mock(PaymentRepository.class),
                mock(InventoryMovementRepository.class),
                orderItemRepository,
                shopRepository,
                inventoryRepository,
                userRepository,
                cartRepository,
                addressRepository
        );
    }

    @Test
    void deliveryCheckoutRequiresAnAddressOwnedByTheCurrentCustomer() {
        User customer = customer();
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        Shop shop = shop();
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(addressRepository.findByIdAndCustomerId(91L, 7L)).thenReturn(Optional.empty());

        assertThrows(com.localcart.exception.ResourceNotFoundException.class,
                () -> service.checkoutCart("customer@example.com", 1L,
                        DeliveryMode.PLATFORM_DELIVERY, 91L));
        verifyNoInteractions(cartRepository);
    }

    @Test
    void deliveryCheckoutCopiesAddressToOrderBeforeTheCustomerCanEditTheirAddressBook() {
        User customer = customer();
        Shop shop = shop();
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(101L);
        when(product.getName()).thenReturn("Rice");

        CartItem cartItem = mock(CartItem.class);
        when(cartItem.getProduct()).thenReturn(product);
        when(cartItem.getQuantity()).thenReturn(2);
        List<CartItem> cartItems = new ArrayList<>(List.of(cartItem));
        Cart cart = mock(Cart.class);
        when(cart.getItems()).thenReturn(cartItems);

        CustomerAddress savedAddress = address();
        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setPrice(40.0);
        inventory.setStockQuantity(5);
        inventory.setAvailable(true);

        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(addressRepository.findByIdAndCustomerId(25L, 7L)).thenReturn(Optional.of(savedAddress));
        when(cartRepository.findByCustomerIdAndShopId(7L, 1L)).thenReturn(Optional.of(cart));
        when(inventoryRepository.findByShopIdAndProductIdForUpdate(1L, 101L)).thenReturn(Optional.of(inventory));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(orderItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = service.checkoutCart("customer@example.com", 1L,
                DeliveryMode.SHOP_DELIVERY, 25L);

        assertEquals(80.0, order.getTotalAmount());
        assertNotNull(order.getDeliveryAddressSnapshot());
        assertEquals("Asha Sharma", order.getDeliveryAddressSnapshot().getRecipientName());
        assertEquals("12, Market Road", order.getDeliveryAddressSnapshot().getAddressLine1());
        assertEquals("560038", order.getDeliveryAddressSnapshot().getPostalCode());
        assertTrue(cartItems.isEmpty());
        verify(cartRepository).save(cart);
    }

    @Test
    void pickupCheckoutDoesNotRequireAStreetAddress() {
        User customer = customer();
        Shop shop = shop();
        Cart cart = mock(Cart.class);
        List<CartItem> cartItems = new ArrayList<>();
        CartItem item = mock(CartItem.class);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(101L);
        when(item.getProduct()).thenReturn(product);
        when(item.getQuantity()).thenReturn(1);
        cartItems.add(item);
        when(cart.getItems()).thenReturn(cartItems);

        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setPrice(20.0);
        inventory.setStockQuantity(2);
        inventory.setAvailable(true);

        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(cartRepository.findByCustomerIdAndShopId(7L, 1L)).thenReturn(Optional.of(cart));
        when(inventoryRepository.findByShopIdAndProductIdForUpdate(1L, 101L)).thenReturn(Optional.of(inventory));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(orderItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = service.checkoutCart("customer@example.com", 1L,
                DeliveryMode.CUSTOMER_PICKUP, null);

        assertNull(order.getDeliveryAddressSnapshot());
        verify(addressRepository, never()).findByIdAndCustomerId(any(), any());
    }

    private User customer() {
        User customer = mock(User.class);
        when(customer.getId()).thenReturn(7L);
        when(customer.getRole()).thenReturn(Role.CUSTOMER);
        when(customer.isActive()).thenReturn(true);
        return customer;
    }

    private Shop shop() {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(1L);
        when(shop.isActive()).thenReturn(true);
        return shop;
    }

    private CustomerAddress address() {
        CustomerAddress address = new CustomerAddress();
        address.setLabel("Home");
        address.setRecipientName("Asha Sharma");
        address.setPhone("+919876543210");
        address.setAddressLine1("12, Market Road");
        address.setLocality("Indiranagar");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPostalCode("560038");
        address.setDefaultAddress(true);
        return address;
    }
}
