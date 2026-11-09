package com.localcart.service;

import com.localcart.dto.CreateShopRequest;
import com.localcart.dto.ShopResponse;
import com.localcart.dto.UpdateShopRequest;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopServiceCoverageTest {

    private ShopRepository shopRepository;
    private UserRepository userRepository;
    private ShopService service;

    @BeforeEach
    void setUp() {
        shopRepository = mock(ShopRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ShopService(shopRepository, userRepository);
    }

    @Test
    void createsShopForActiveShopkeeper() {
        User owner = user(21L, "Owner", "owner@example.com", Role.SHOPKEEPER, true);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.save(any(Shop.class))).thenAnswer(invocation -> {
            Shop saved = invocation.getArgument(0);
            setId(saved, 31L);
            return saved;
        });

        ShopResponse response = service.createShop("owner@example.com", createRequest());

        assertEquals(31L, response.getId());
        assertEquals("Fresh Store", response.getName());
        assertEquals("Owner", response.getOwnerName());
        assertTrue(response.isActive());
        verify(shopRepository).save(argThat(shop -> shop.getOwner() == owner && shop.isActive()));
    }

    @Test
    void rejectsShopCreationWithoutAuthenticatedEmail() {
        assertThrows(BadRequestException.class, () -> service.createShop(null, createRequest()));
        verifyNoInteractions(userRepository, shopRepository);
    }

    @Test
    void rejectsUnknownShopkeeper() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.createShop("missing@example.com", createRequest()));
    }

    @Test
    void rejectsCustomerTryingToCreateShop() {
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(22L, "Customer", "customer@example.com", Role.CUSTOMER, true)));
        assertThrows(BadRequestException.class,
                () -> service.createShop("customer@example.com", createRequest()));
        verifyNoInteractions(shopRepository);
    }

    @Test
    void rejectsInactiveShopkeeper() {
        when(userRepository.findByEmail("inactive@example.com"))
                .thenReturn(Optional.of(user(23L, "Inactive", "inactive@example.com", Role.SHOPKEEPER, false)));
        assertThrows(BadRequestException.class,
                () -> service.createShop("inactive@example.com", createRequest()));
    }

    @Test
    void returnsAllShops() {
        Shop first = shop(1L, "First", null);
        Shop second = shop(2L, "Second", null);
        when(shopRepository.findAll()).thenReturn(List.of(first, second));

        List<ShopResponse> response = service.getAllShops();

        assertEquals(2, response.size());
        assertEquals("Second", response.get(1).getName());
    }

    @Test
    void getsShopById() {
        when(shopRepository.findById(3L)).thenReturn(Optional.of(shop(3L, "Corner Shop", null)));
        assertEquals("Corner Shop", service.getShopById(3L).getName());
    }

    @Test
    void rejectsUnknownShopId() {
        when(shopRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getShopById(404L));
    }

    @Test
    void returnsShopsOwnedByExistingUser() {
        when(userRepository.existsById(24L)).thenReturn(true);
        when(shopRepository.findByOwnerId(24L)).thenReturn(List.of(shop(4L, "My Store", null)));
        List<ShopResponse> response = service.getShopsByOwner(24L);
        assertEquals(1, response.size());
        assertEquals("My Store", response.get(0).getName());
    }

    @Test
    void rejectsShopListForUnknownOwner() {
        when(userRepository.existsById(404L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.getShopsByOwner(404L));
        verifyNoInteractions(shopRepository);
    }

    @Test
    void updatesOwnedShopDetails() throws Exception {
        User owner = user(25L, "Owner", "owner@example.com", Role.SHOPKEEPER, true);
        Shop shop = shop(5L, "Old Name", owner);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(shopRepository.save(shop)).thenReturn(shop);
        UpdateShopRequest request = updateRequest("Updated Name", "9999999999", "New Address");

        ShopResponse response = service.updateShop(5L, "owner@example.com", request);

        assertEquals("Updated Name", response.getName());
        assertEquals("9999999999", response.getPhone());
        assertEquals("New Address", response.getAddress());
    }

    @Test
    void rejectsUpdatingAnotherOwnersShop() {
        User currentOwner = user(26L, "Current", "current@example.com", Role.SHOPKEEPER, true);
        User actualOwner = user(27L, "Actual", "actual@example.com", Role.SHOPKEEPER, true);
        when(userRepository.findByEmail("current@example.com")).thenReturn(Optional.of(currentOwner));
        when(shopRepository.findById(6L)).thenReturn(Optional.of(shop(6L, "Other Shop", actualOwner)));

        assertThrows(BadRequestException.class,
                () -> service.updateShop(6L, "current@example.com", updateRequest("X", "1", "Y")));
        verify(shopRepository, never()).save(any(Shop.class));
    }

    @Test
    void deactivatesOwnedShop() {
        User owner = user(28L, "Owner", "owner@example.com", Role.SHOPKEEPER, true);
        Shop shop = shop(7L, "My Store", owner);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.findById(7L)).thenReturn(Optional.of(shop));
        when(shopRepository.save(shop)).thenReturn(shop);

        ShopResponse response = service.deactivateShop(7L, "owner@example.com");

        assertFalse(response.isActive());
        assertFalse(shop.isActive());
    }

    @Test
    void rejectsDeactivatingShopOwnedBySomeoneElse() {
        User currentOwner = user(29L, "Current", "current@example.com", Role.SHOPKEEPER, true);
        User actualOwner = user(30L, "Actual", "actual@example.com", Role.SHOPKEEPER, true);
        when(userRepository.findByEmail("current@example.com")).thenReturn(Optional.of(currentOwner));
        when(shopRepository.findById(8L)).thenReturn(Optional.of(shop(8L, "Other", actualOwner)));

        assertThrows(BadRequestException.class, () -> service.deactivateShop(8L, "current@example.com"));
        verify(shopRepository, never()).save(any(Shop.class));
    }

    @Test
    void rejectsUpdatingNonexistentShop() {
        User owner = user(31L, "Owner", "owner@example.com", Role.SHOPKEEPER, true);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.findById(909L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateShop(909L, "owner@example.com", updateRequest("X", "1", "Y")));
    }

    private static CreateShopRequest createRequest() {
        CreateShopRequest request = new CreateShopRequest();
        request.setName("Fresh Store");
        request.setPhone("9876543210");
        request.setAddress("Bengaluru");
        return request;
    }

    private static UpdateShopRequest updateRequest(String name, String phone, String address) throws Exception {
        UpdateShopRequest request = new UpdateShopRequest();
        setField(request, "name", name);
        setField(request, "phone", phone);
        setField(request, "address", address);
        return request;
    }

    private static User user(Long id, String name, String email, Role role, boolean active) {
        User user = new User();
        setId(user, id);
        user.setName(name);
        user.setEmail(email);
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private static Shop shop(Long id, String name, User owner) {
        Shop shop = new Shop();
        setId(shop, id);
        shop.setName(name);
        shop.setOwner(owner);
        shop.setOwnerName(owner == null ? null : owner.getName());
        shop.setPhone("9876543210");
        shop.setAddress("Bengaluru");
        shop.setActive(true);
        return shop;
    }

    private static void setId(Object target, Long id) {
        setField(target, "id", id);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
