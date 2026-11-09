package com.localcart.service;

import com.localcart.dto.AddToCartRequest;
import com.localcart.dto.CartResponse;
import com.localcart.entity.Cart;
import com.localcart.entity.CartItem;
import com.localcart.entity.Product;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.CartItemRepository;
import com.localcart.repository.CartRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceTest {

    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private UserRepository userRepository;
    private ShopRepository shopRepository;
    private ShopInventoryRepository inventoryRepository;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        userRepository = mock(UserRepository.class);
        shopRepository = mock(ShopRepository.class);
        inventoryRepository = mock(ShopInventoryRepository.class);
        cartService = new CartService(
                cartRepository,
                cartItemRepository,
                userRepository,
                shopRepository,
                inventoryRepository
        );
    }

    @Test
    void cartRejectsUsersWhoAreNotCustomers() {
        User shopkeeper = user(Role.SHOPKEEPER, 12L, true);
        when(userRepository.findByEmail("shopkeeper@example.com"))
                .thenReturn(Optional.of(shopkeeper));

        assertThrows(BadRequestException.class,
                () -> cartService.getCart("shopkeeper@example.com", 1L));

        verifyNoInteractions(shopRepository, cartRepository);
    }

    @Test
    void cartRejectsInactiveCustomers() {
        User customer = user(Role.CUSTOMER, 10L, false);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));

        assertThrows(BadRequestException.class,
                () -> cartService.getCart("customer@example.com", 1L));

        verifyNoInteractions(shopRepository, cartRepository);
    }

    @Test
    void addToCartRejectsUnavailableInventory() {
        arrangeActiveCustomerAndShop();
        Product product = product(101L, "Rice", "Staples");
        ShopInventory inventory = inventory(product, 100.0, 0, false);
        when(inventoryRepository.findByShopIdAndProductId(1L, 101L))
                .thenReturn(Optional.of(inventory));

        AddToCartRequest request = addRequest(101L, 1);

        assertThrows(BadRequestException.class,
                () -> cartService.addToCart("customer@example.com", 1L, request));

        verify(cartRepository, never()).save(any(Cart.class));
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void addToCartRejectsQuantityAboveAvailableStock() {
        arrangeActiveCustomerAndShop();
        Product product = product(101L, "Rice", "Staples");
        ShopInventory inventory = inventory(product, 100.0, 2, true);
        Cart cart = mock(Cart.class);
        when(cart.getId()).thenReturn(50L);
        when(cartRepository.findByCustomerIdAndShopId(10L, 1L))
                .thenReturn(Optional.of(cart));
        when(inventoryRepository.findByShopIdAndProductId(1L, 101L))
                .thenReturn(Optional.of(inventory));
        when(cartItemRepository.findByCartIdAndProductId(50L, 101L))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> cartService.addToCart("customer@example.com", 1L, addRequest(101L, 3)));

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void getCartCalculatesLineAndGrandTotalsFromCurrentInventoryPrice() {
        User customer = user(Role.CUSTOMER, 10L, true);
        Shop shop = shop(1L, "Local Fresh");
        Product product = product(101L, "Rice", "Staples");
        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        Cart cart = new Cart();
        cart.setCustomer(customer);
        cart.setShop(shop);
        cart.setItems(new ArrayList<>(List.of(cartItem)));

        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(cartRepository.findByCustomerIdAndShopId(10L, 1L))
                .thenReturn(Optional.of(cart));
        when(inventoryRepository.findByShopIdAndProductId(1L, 101L))
                .thenReturn(Optional.of(inventory(product, 125.50, 8, true)));

        CartResponse response = cartService.getCart("customer@example.com", 1L);

        assertEquals(1, response.getItems().size());
        assertEquals(251.00, response.getItems().get(0).getLineTotal(), 0.001);
        assertEquals(251.00, response.getTotalAmount(), 0.001);
        assertTrue(response.getItems().get(0).isAvailable());
        assertEquals(8, response.getItems().get(0).getAvailableStock());
    }

    private void arrangeActiveCustomerAndShop() {
        User customer = user(Role.CUSTOMER, 10L, true);
        Shop shop = shop(1L, "Local Fresh");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
    }

    private User user(Role role, Long id, boolean active) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(active);
        return user;
    }

    private Shop shop(Long id, String name) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getName()).thenReturn(name);
        when(shop.isActive()).thenReturn(true);
        return shop;
    }

    private Product product(Long id, String name, String category) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn(name);
        when(product.getCategory()).thenReturn(category);
        return product;
    }

    private ShopInventory inventory(Product product, double price, int stock, boolean available) {
        ShopInventory inventory = new ShopInventory();
        inventory.setProduct(product);
        inventory.setPrice(price);
        inventory.setStockQuantity(stock);
        inventory.setAvailable(available);
        return inventory;
    }

    private AddToCartRequest addRequest(Long productId, int quantity) {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        return request;
    }
}
