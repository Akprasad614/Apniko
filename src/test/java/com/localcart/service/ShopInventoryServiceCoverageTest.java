package com.localcart.service;

import com.localcart.dto.AddInventoryRequest;
import com.localcart.dto.RestockInventoryRequest;
import com.localcart.dto.UpdateInventoryRequest;
import com.localcart.dto.UpdateLowStockThresholdRequest;
import com.localcart.entity.InventoryMovement;
import com.localcart.entity.InventoryMovementType;
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
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopInventoryServiceCoverageTest {

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
        service = new ShopInventoryService(inventoryRepository, movementRepository,
                shopRepository, productRepository, userRepository);
    }

    @Test
    void addInventoryCreatesInitialMovementAndMarksZeroStockUnavailable() {
        User owner = owner();
        Shop shop = shop(1L, owner);
        Product product = product(101L);
        arrangeOwnerAndShop(owner, shop);
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByShopIdAndProductId(1L, 101L))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(ShopInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddInventoryRequest request = new AddInventoryRequest();
        request.setProductId(101L);
        request.setPrice(35.50);
        request.setStockQuantity(0);

        var response = service.addInventory("owner@example.com", 1L, request);

        assertEquals(0, response.getStockQuantity());
        assertFalse(response.isAvailable());
        assertEquals(5, response.getLowStockThreshold());
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertEquals(InventoryMovementType.INITIAL_STOCK, captor.getValue().getMovementType());
        assertEquals(0, captor.getValue().getPreviousQuantity());
        assertEquals(0, captor.getValue().getNewQuantity());
    }

    @Test
    void addInventoryRejectsNegativePriceOrStock() {
        User owner = owner();
        Shop shop = shop(1L, owner);
        arrangeOwnerAndShop(owner, shop);
        when(productRepository.findById(101L)).thenReturn(Optional.of(product(101L)));
        AddInventoryRequest request = new AddInventoryRequest();
        request.setProductId(101L);
        request.setPrice(-1.0);
        request.setStockQuantity(5);

        assertThrows(BadRequestException.class,
                () -> service.addInventory("owner@example.com", 1L, request));
        verify(inventoryRepository, never()).save(any(ShopInventory.class));
        verifyNoInteractions(movementRepository);
    }

    @Test
    void addInventoryRejectsDuplicateProductInSameShop() {
        User owner = owner();
        Shop shop = shop(1L, owner);
        arrangeOwnerAndShop(owner, shop);
        when(productRepository.findById(101L)).thenReturn(Optional.of(product(101L)));
        when(inventoryRepository.findByShopIdAndProductId(1L, 101L))
                .thenReturn(Optional.of(inventory(shop, product(101L), 3)));
        AddInventoryRequest request = new AddInventoryRequest();
        request.setProductId(101L);
        request.setPrice(25.0);
        request.setStockQuantity(4);

        assertThrows(BadRequestException.class,
                () -> service.addInventory("owner@example.com", 1L, request));
        verify(inventoryRepository, never()).save(any(ShopInventory.class));
    }

    @Test
    void updateInventoryChangesPriceAndAvailabilityWithoutMovementWhenQuantityIsSame() {
        User owner = owner();
        Shop shop = shop(1L, owner);
        ShopInventory inventory = inventory(shop, product(101L), 8);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(inventoryRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        UpdateInventoryRequest request = new UpdateInventoryRequest();
        request.setPrice(42.0);
        request.setStockQuantity(8);
        request.setAvailable(false);

        var response = service.updateInventory("owner@example.com", 50L, request);

        assertEquals(42.0, response.getPrice());
        assertFalse(response.isAvailable());
        verify(movementRepository, never()).save(any(InventoryMovement.class));
    }

    @Test
    void updateInventoryRecordsAdjustmentWhenStockChangesToZero() {
        User owner = owner();
        Shop shop = shop(1L, owner);
        ShopInventory inventory = inventory(shop, product(101L), 8);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(inventoryRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        UpdateInventoryRequest request = new UpdateInventoryRequest();
        request.setPrice(40.0);
        request.setStockQuantity(0);
        request.setAvailable(true);

        var response = service.updateInventory("owner@example.com", 50L, request);

        assertEquals(0, response.getStockQuantity());
        assertFalse(response.isAvailable());
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertEquals(InventoryMovementType.ADJUSTMENT, captor.getValue().getMovementType());
        assertEquals(-8, captor.getValue().getQuantityChange());
    }

    @Test
    void updateLowStockThresholdPersistsNewThreshold() {
        User owner = owner();
        Shop shop = shop(1L, owner);
        ShopInventory inventory = inventory(shop, product(101L), 8);
        arrangeOwnerAndShop(owner, shop);
        when(inventoryRepository.findByIdAndShopIdForUpdate(50L, 1L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        UpdateLowStockThresholdRequest request = new UpdateLowStockThresholdRequest();
        request.setLowStockThreshold(12);

        var response = service.updateLowStockThreshold("owner@example.com", 1L, 50L, request);

        assertEquals(12, response.getLowStockThreshold());
        assertTrue(response.isLowStock());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void movementHistoryDoesNotExposeInventoryFromAnotherShop() {
        User owner = owner();
        Shop shopOne = shop(1L, owner);
        Shop shopTwo = shop(2L, owner);
        ShopInventory inventory = inventory(shopTwo, product(101L), 4);
        arrangeOwnerAndShop(owner, shopOne);
        when(inventoryRepository.findById(50L)).thenReturn(Optional.of(inventory));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMovementHistory("owner@example.com", 1L, 50L));
        verify(movementRepository, never())
                .findByInventoryIdAndShopIdOrderByCreatedAtDescIdDesc(anyLong(), anyLong());
    }

    @Test
    void movementHistoryReturnsMappedListForMatchingShop() {
        User owner = owner();
        Shop shop = shop(1L, owner);
        ShopInventory inventory = inventory(shop, product(101L), 4);
        arrangeOwnerAndShop(owner, shop);
        when(inventoryRepository.findById(50L)).thenReturn(Optional.of(inventory));
        when(movementRepository.findByInventoryIdAndShopIdOrderByCreatedAtDescIdDesc(50L, 1L))
                .thenReturn(List.of());

        assertTrue(service.getMovementHistory("owner@example.com", 1L, 50L).isEmpty());
        verify(movementRepository)
                .findByInventoryIdAndShopIdOrderByCreatedAtDescIdDesc(50L, 1L);
    }

    @Test
    void inventoryOperationsRejectInactiveOrWrongRoleAccounts() {
        User customer = mock(User.class);
        when(customer.getRole()).thenReturn(Role.CUSTOMER);
        when(customer.isActive()).thenReturn(true);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        assertThrows(BadRequestException.class,
                () -> service.getMyInventory("customer@example.com", 1L));

        User inactive = mock(User.class);
        when(inactive.getRole()).thenReturn(Role.SHOPKEEPER);
        when(inactive.isActive()).thenReturn(false);
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactive));

        assertThrows(BadRequestException.class,
                () -> service.getMyInventory("inactive@example.com", 1L));
        verifyNoInteractions(shopRepository);
    }

    private void arrangeOwnerAndShop(User owner, Shop shop) {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
    }

    private static User owner() {
        User owner = new User();
        owner.setName("Test Shopkeeper");
        owner.setEmail("owner@example.com");
        owner.setRole(Role.SHOPKEEPER);
        owner.setActive(true);
        setEntityId(owner, 12L);
        return owner;
    }

    private static Shop shop(Long id, User owner) {
        Shop shop = new Shop();
        shop.setName("Local Fresh " + id);
        shop.setOwner(owner);
        shop.setActive(true);
        setEntityId(shop, id);
        return shop;
    }

    private static Product product(Long id) {
        Product product = new Product();
        product.setName("Rice " + id);
        product.setCategory("Staples");
        setEntityId(product, id);
        return product;
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

    private static ShopInventory inventory(Shop shop, Product product, int stock) {
        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setPrice(20.0);
        inventory.setStockQuantity(stock);
        inventory.setAvailable(stock > 0);
        inventory.setLowStockThreshold(5);
        return inventory;
    }
}
