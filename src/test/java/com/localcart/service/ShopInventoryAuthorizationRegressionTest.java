package com.localcart.service;

import com.localcart.dto.RestockInventoryRequest;
import com.localcart.dto.UpdateInventoryRequest;
import com.localcart.entity.Product;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.InventoryMovementRepository;
import com.localcart.repository.ProductRepository;
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

class ShopInventoryAuthorizationRegressionTest {

    private ShopInventoryRepository inventoryRepository;
    private InventoryMovementRepository movementRepository;
    private ShopRepository shopRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private ShopInventoryService service;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(ShopInventoryRepository.class);
        movementRepository = mock(InventoryMovementRepository.class);
        shopRepository = mock(ShopRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ShopInventoryService(
                inventoryRepository, movementRepository, shopRepository, productRepository, userRepository
        );
    }

    @Test
    void shopkeeperCannotUpdateInventoryOwnedByAnotherShop() {
        User caller = user(12L, Role.SHOPKEEPER, true);
        Shop otherShop = shop(2L, user(99L, Role.SHOPKEEPER, true));
        ShopInventory foreignInventory = inventory(otherShop);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(caller));
        when(inventoryRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(foreignInventory));

        UpdateInventoryRequest request = new UpdateInventoryRequest();
        request.setPrice(140.0);
        request.setStockQuantity(30);
        request.setAvailable(true);

        assertThrows(BadRequestException.class, () ->
                service.updateInventory("owner@example.com", 70L, request)
        );

        verify(inventoryRepository, never()).save(any(ShopInventory.class));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shopkeeperCannotRestockInventoryIdFromAnotherShop() {
        User caller = user(12L, Role.SHOPKEEPER, true);
        Shop ownShop = shop(1L, caller);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(caller));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(ownShop));
        when(inventoryRepository.findByIdAndShopIdForUpdate(70L, 1L)).thenReturn(Optional.empty());

        RestockInventoryRequest request = new RestockInventoryRequest();
        request.setQuantity(5);

        assertThrows(ResourceNotFoundException.class, () ->
                service.restock("owner@example.com", 1L, 70L, request)
        );

        verify(inventoryRepository, never()).save(any(ShopInventory.class));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shopkeeperCannotReadMovementHistoryForInventoryFromAnotherShop() {
        User caller = user(12L, Role.SHOPKEEPER, true);
        Shop ownShop = shop(1L, caller);
        Shop otherShop = shop(2L, user(99L, Role.SHOPKEEPER, true));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(caller));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(ownShop));
        when(inventoryRepository.findById(70L)).thenReturn(Optional.of(inventory(otherShop)));

        assertThrows(ResourceNotFoundException.class, () ->
                service.getMovementHistory("owner@example.com", 1L, 70L)
        );

        verify(movementRepository, never())
                .findByInventoryIdAndShopIdOrderByCreatedAtDescIdDesc(70L, 1L);
    }

    @Test
    void inactiveShopkeeperCannotAccessInventory() {
        User inactiveShopkeeper = user(12L, Role.SHOPKEEPER, false);
        when(userRepository.findByEmail("inactive@example.com"))
                .thenReturn(Optional.of(inactiveShopkeeper));

        assertThrows(BadRequestException.class, () ->
                service.getMyInventory("inactive@example.com", 1L)
        );

        verifyNoInteractions(shopRepository, inventoryRepository, movementRepository);
    }

    @Test
    void customerRoleCannotAccessShopkeeperInventoryOperations() {
        User customer = user(10L, Role.CUSTOMER, true);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));

        assertThrows(BadRequestException.class, () ->
                service.getMyInventory("customer@example.com", 1L)
        );

        verifyNoInteractions(shopRepository, inventoryRepository, movementRepository);
    }

    private User user(Long id, Role role, boolean active) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(active);
        return user;
    }

    private Shop shop(Long id, User owner) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getOwner()).thenReturn(owner);
        return shop;
    }

    private ShopInventory inventory(Shop shop) {
        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(mock(Product.class));
        inventory.setPrice(100.0);
        inventory.setStockQuantity(10);
        inventory.setAvailable(true);
        return inventory;
    }
}
