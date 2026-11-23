package com.localcart.service;

import com.localcart.dto.AdminShopResponse;
import com.localcart.dto.AssignShopOwnerRequest;
import com.localcart.dto.CreateManagedUserRequest;
import com.localcart.dto.UpdateActiveStatusRequest;
import com.localcart.dto.UserResponse;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminServiceCoverageTest {

    private UserRepository userRepository;
    private ShopRepository shopRepository;
    private PasswordEncoder passwordEncoder;
    private AdminService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        shopRepository = mock(ShopRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AdminService(userRepository, shopRepository, passwordEncoder);
    }

    @Test
    void createsShopkeeperWithNormalizedDetailsAndEncodedPassword() {
        CreateManagedUserRequest request = managedRequest("  New Shopkeeper ", "  OWNER@EXAMPLE.COM  ", "password123");
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            setId(saved, 41L);
            return saved;
        });

        UserResponse response = service.createShopkeeper(request);

        assertEquals(41L, response.getId());
        assertEquals("New Shopkeeper", response.getName());
        assertEquals("owner@example.com", response.getEmail());
        assertEquals(Role.SHOPKEEPER, response.getRole());
        assertTrue(response.isActive());
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(argThat(user ->
                "encoded-password".equals(user.getPassword()) && user.getRole() == Role.SHOPKEEPER));
    }

    @Test
    void createsDeliveryPartnerWithDeliveryRole() {
        CreateManagedUserRequest request = managedRequest("Driver", "driver@example.com", "password123");
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.createDeliveryPartner(request);

        assertEquals(Role.DELIVERY_PARTNER, response.getRole());
        assertTrue(response.isActive());
    }

    @Test
    void rejectsDuplicateManagedUserEmail() {
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(true);
        assertThrows(BadRequestException.class,
                () -> service.createShopkeeper(managedRequest("Owner", " Owner@Example.com ", "password123")));
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void returnsAllUsersWhenRoleFilterIsNull() {
        User first = user(1L, "One", "one@example.com", Role.CUSTOMER, true);
        User second = user(2L, "Two", "two@example.com", Role.ADMIN, true);
        when(userRepository.findAll()).thenReturn(List.of(first, second));

        List<UserResponse> result = service.getUsers(null);

        assertEquals(2, result.size());
        assertEquals(Role.ADMIN, result.get(1).getRole());
        verify(userRepository).findAll();
        verify(userRepository, never()).findByRole(any());
    }

    @Test
    void filtersUsersByRequestedRole() {
        User owner = user(3L, "Owner", "owner@example.com", Role.SHOPKEEPER, true);
        when(userRepository.findByRole(Role.SHOPKEEPER)).thenReturn(List.of(owner));

        List<UserResponse> result = service.getUsers(Role.SHOPKEEPER);

        assertEquals(1, result.size());
        assertEquals(Role.SHOPKEEPER, result.get(0).getRole());
    }

    @Test
    void getsUserById() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(
                user(7L, "Customer", "customer@example.com", Role.CUSTOMER, true)));

        UserResponse response = service.getUser(7L);

        assertEquals("customer@example.com", response.getEmail());
        assertEquals(7L, response.getId());
    }

    @Test
    void rejectsUnknownUserId() {
        when(userRepository.findById(77L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getUser(77L));
    }

    @Test
    void updatesRegularUsersActiveStatus() {
        User target = user(8L, "Driver", "driver@example.com", Role.DELIVERY_PARTNER, true);
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        UpdateActiveStatusRequest request = activeRequest(false);

        UserResponse response = service.updateUserActiveStatus(8L, request, "admin@example.com");

        assertFalse(response.isActive());
        assertFalse(target.isActive());
        verify(userRepository).save(target);
    }

    @Test
    void preventsAdminFromDeactivatingOwnAccount() {
        User admin = user(9L, "Admin", "admin@example.com", Role.ADMIN, true);
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class,
                () -> service.updateUserActiveStatus(9L, activeRequest(false), "ADMIN@example.com"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void preventsDeactivationOfLastActiveAdmin() {
        User otherAdmin = user(10L, "Other Admin", "other@example.com", Role.ADMIN, true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(otherAdmin));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(BadRequestException.class,
                () -> service.updateUserActiveStatus(10L, activeRequest(false), "admin@example.com"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void allowsAdminDeactivationWhenAnotherActiveAdminExists() {
        User otherAdmin = user(11L, "Other Admin", "other@example.com", Role.ADMIN, true);
        when(userRepository.findById(11L)).thenReturn(Optional.of(otherAdmin));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(2L);
        when(userRepository.save(otherAdmin)).thenReturn(otherAdmin);

        UserResponse response = service.updateUserActiveStatus(11L, activeRequest(false), "admin@example.com");

        assertFalse(response.isActive());
        verify(userRepository).save(otherAdmin);
    }

    @Test
    void mapsOwnedAndUnassignedShopsForAdminList() throws Exception {
        User owner = user(12L, "Shop Owner", "owner@example.com", Role.SHOPKEEPER, true);
        Shop owned = shop(1L, "Owned Shop", owner);
        Shop unassigned = shop(2L, "Unassigned Shop", null);
        unassigned.setOwnerName("Legacy Owner Name");
        when(shopRepository.findAll()).thenReturn(List.of(owned, unassigned));

        List<AdminShopResponse> result = service.getShops();

        assertEquals(2, result.size());
        assertEquals("owner@example.com", result.get(0).getOwnerEmail());
        assertNull(result.get(1).getOwnerId());
        assertEquals("Legacy Owner Name", result.get(1).getOwnerName());
    }

    @Test
    void assignsActiveShopkeeperAsShopOwner() throws Exception {
        Shop shop = shop(4L, "Market", null);
        User owner = user(14L, "Owner", "owner@example.com", Role.SHOPKEEPER, true);
        when(shopRepository.findById(4L)).thenReturn(Optional.of(shop));
        when(userRepository.findById(14L)).thenReturn(Optional.of(owner));
        when(shopRepository.save(shop)).thenReturn(shop);
        AssignShopOwnerRequest request = new AssignShopOwnerRequest();
        request.setOwnerId(14L);

        AdminShopResponse response = service.assignShopOwner(4L, request);

        assertEquals(14L, response.getOwnerId());
        assertEquals("Owner", response.getOwnerName());
        assertEquals("owner@example.com", response.getOwnerEmail());
    }

    @Test
    void rejectsAssigningNonShopkeeper() {
        Shop shop = shop(5L, "Market", null);
        User customer = user(15L, "Customer", "customer@example.com", Role.CUSTOMER, true);
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(userRepository.findById(15L)).thenReturn(Optional.of(customer));
        AssignShopOwnerRequest request = new AssignShopOwnerRequest();
        request.setOwnerId(15L);

        assertThrows(BadRequestException.class, () -> service.assignShopOwner(5L, request));
        verify(shopRepository, never()).save(any(Shop.class));
    }

    @Test
    void rejectsAssigningInactiveShopkeeper() {
        Shop shop = shop(6L, "Market", null);
        User owner = user(16L, "Inactive Owner", "inactive@example.com", Role.SHOPKEEPER, false);
        when(shopRepository.findById(6L)).thenReturn(Optional.of(shop));
        when(userRepository.findById(16L)).thenReturn(Optional.of(owner));
        AssignShopOwnerRequest request = new AssignShopOwnerRequest();
        request.setOwnerId(16L);

        assertThrows(BadRequestException.class, () -> service.assignShopOwner(6L, request));
    }

    @Test
    void rejectsMissingShopWhenAssigningOwner() {
        when(shopRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.assignShopOwner(99L, ownerRequest(2L)));
        verifyNoInteractions(userRepository);
    }

    @Test
    void updatesShopActiveStatus() {
        Shop shop = shop(7L, "Market", null);
        when(shopRepository.findById(7L)).thenReturn(Optional.of(shop));
        when(shopRepository.save(shop)).thenReturn(shop);

        AdminShopResponse response = service.updateShopActiveStatus(7L, activeRequest(false));

        assertFalse(response.isActive());
        assertFalse(shop.isActive());
    }

    private static CreateManagedUserRequest managedRequest(String name, String email, String password) {
        CreateManagedUserRequest request = new CreateManagedUserRequest();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static UpdateActiveStatusRequest activeRequest(boolean active) {
        UpdateActiveStatusRequest request = new UpdateActiveStatusRequest();
        request.setActive(active);
        return request;
    }

    private static AssignShopOwnerRequest ownerRequest(Long id) {
        AssignShopOwnerRequest request = new AssignShopOwnerRequest();
        request.setOwnerId(id);
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
        shop.setActive(true);
        shop.setPhone("9876543210");
        shop.setAddress("Bengaluru");
        return shop;
    }

    private static void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
