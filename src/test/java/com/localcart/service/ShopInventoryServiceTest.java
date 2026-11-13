package com.localcart.service;

import com.localcart.dto.InventoryResponse;
import com.localcart.dto.RestockInventoryRequest;
import com.localcart.entity.InventoryMovement;
import com.localcart.entity.Product;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;
import com.localcart.repository.InventoryMovementRepository;
import com.localcart.repository.ProductRepository;
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

class ShopInventoryServiceTest {

    private ShopInventoryRepository inventoryRepository;
    private InventoryMovementRepository movementRepository;
    private ShopRepository shopRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private ProductImageStorageService imageStorageService;
    private ShopInventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(ShopInventoryRepository.class);
        movementRepository = mock(InventoryMovementRepository.class);
        shopRepository = mock(ShopRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        imageStorageService = mock(ProductImageStorageService.class);
        inventoryService = new ShopInventoryService(
                inventoryRepository,
                movementRepository,
                shopRepository,
                productRepository,
                userRepository,
                imageStorageService
        );
    }

    @Test
    void uploadProductImageStoresPhotoForInventoryOwnedByTheShopkeeper() {
        User owner = user(12L, "owner@example.com");
        Shop shop = shop(1L, owner);
        Product product = product(101L);
        ShopInventory inventory = inventory(shop, product, 5, true, 5);
        inventory.setImageUrl("/api/media/products/old-photo.png");
        arrangeOwnerAndShop(owner, shop);
        when(inventoryRepository.findByIdAndShopIdForUpdate(20L, 1L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        MultipartFile file = mock(MultipartFile.class);
        when(imageStorageService.store(file))
                .thenReturn("/api/media/products/new-photo.jpg");

        InventoryResponse response = inventoryService.uploadProductImage(
                "owner@example.com", 1L, 20L, file);

        assertEquals("/api/media/products/new-photo.jpg", response.getImageUrl());
        verify(inventoryRepository).save(inventory);
        verify(imageStorageService).delete("/api/media/products/old-photo.png");
    }

    @Test
    void uploadProductImageRejectsInventoryFromAnotherShopOwner() {
        User owner = user(12L, "owner@example.com");
        User otherOwner = user(99L, "other@example.com");
        Shop otherShop = shop(1L, otherOwner);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(otherShop));

        MultipartFile file = mock(MultipartFile.class);

        assertThrows(BadRequestException.class,
                () -> inventoryService.uploadProductImage("owner@example.com", 1L, 20L, file));
        verifyNoInteractions(imageStorageService);
        verify(inventoryRepository, never()).save(any(ShopInventory.class));
    }

    @Test
    void restockIncreasesStockAndRecordsMovement() {
        User owner = user(12L, "owner@example.com");
        Shop shop = shop(1L, owner);
        ShopInventory inventory = inventory(shop, product(101L), 5, true, 5);
        arrangeOwnerAndShop(owner, shop);
        when(inventoryRepository.findByIdAndShopIdForUpdate(20L, 1L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        RestockInventoryRequest request = new RestockInventoryRequest();
        request.setQuantity(10);
        request.setNote("  New supplier delivery  ");

        InventoryResponse response = inventoryService.restock(
                "owner@example.com", 1L, 20L, request);

        assertEquals(15, inventory.getStockQuantity());
        assertTrue(inventory.isAvailable());
        assertEquals(15, response.getStockQuantity());
        verify(movementRepository).save(argThat(movement ->
                movement.getPreviousQuantity() == 5
                        && movement.getNewQuantity() == 15
                        && movement.getQuantityChange() == 10
                        && "New supplier delivery".equals(movement.getNote())
        ));
    }

    @Test
    void restockRejectsIntegerOverflowWithoutSaving() {
        User owner = user(12L, "owner@example.com");
        Shop shop = shop(1L, owner);
        ShopInventory inventory = inventory(shop, product(101L), Integer.MAX_VALUE, true, 5);
        arrangeOwnerAndShop(owner, shop);
        when(inventoryRepository.findByIdAndShopIdForUpdate(20L, 1L))
                .thenReturn(Optional.of(inventory));

        RestockInventoryRequest request = new RestockInventoryRequest();
        request.setQuantity(1);

        assertThrows(BadRequestException.class,
                () -> inventoryService.restock("owner@example.com", 1L, 20L, request));

        verify(inventoryRepository, never()).save(any(ShopInventory.class));
        verify(movementRepository, never()).save(any(InventoryMovement.class));
    }

    @Test
    void shopkeeperCannotRestockAnotherOwnersShop() {
        User currentOwner = user(12L, "owner@example.com");
        User differentOwner = user(99L, "other@example.com");
        Shop shop = shop(1L, differentOwner);
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(currentOwner));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));

        RestockInventoryRequest request = new RestockInventoryRequest();
        request.setQuantity(5);

        assertThrows(BadRequestException.class,
                () -> inventoryService.restock("owner@example.com", 1L, 20L, request));

        verify(inventoryRepository, never()).findByIdAndShopIdForUpdate(anyLong(), anyLong());
        verify(inventoryRepository, never()).save(any(ShopInventory.class));
    }

    @Test
    void lowStockUsesDefaultThresholdForLegacyRowsWithNullThreshold() {
        User owner = user(12L, "owner@example.com");
        Shop shop = shop(1L, owner);
        ShopInventory lowStock = inventory(shop, product(101L), 4, true, null);
        ShopInventory healthy = inventory(shop, product(102L), 9, true, null);
        arrangeOwnerAndShop(owner, shop);
        when(inventoryRepository.findByShopId(1L)).thenReturn(List.of(lowStock, healthy));

        List<InventoryResponse> response = inventoryService.getLowStockItems("owner@example.com", 1L);

        assertEquals(1, response.size());
        assertEquals(4, response.get(0).getStockQuantity());
        assertEquals(5, response.get(0).getLowStockThreshold());
        assertTrue(response.get(0).isLowStock());
    }

    private void arrangeOwnerAndShop(User owner, Shop shop) {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
    }

    private User user(Long id, String email) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.getName()).thenReturn("Test Shopkeeper");
        when(user.getRole()).thenReturn(Role.SHOPKEEPER);
        when(user.isActive()).thenReturn(true);
        return user;
    }

    private Shop shop(Long id, User owner) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getName()).thenReturn("Local Fresh");
        when(shop.getOwner()).thenReturn(owner);
        return shop;
    }

    private Product product(Long id) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn("Rice " + id);
        when(product.getCategory()).thenReturn("Staples");
        return product;
    }

    private ShopInventory inventory(Shop shop, Product product, int stock,
                                    boolean available, Integer threshold) {
        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setPrice(100.0);
        inventory.setStockQuantity(stock);
        inventory.setAvailable(available);
        inventory.setLowStockThreshold(threshold);
        return inventory;
    }
}
