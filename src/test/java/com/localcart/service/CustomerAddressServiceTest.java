package com.localcart.service;

import com.localcart.dto.CustomerAddressRequest;
import com.localcart.dto.CustomerAddressResponse;
import com.localcart.entity.OrderDeliveryAddress;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.repository.CustomerAddressRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerAddressServiceTest {

    private CustomerAddressRepository addressRepository;
    private UserRepository userRepository;
    private CustomerAddressService service;

    @BeforeEach
    void setUp() {
        addressRepository = mock(CustomerAddressRepository.class);
        userRepository = mock(UserRepository.class);
        service = new CustomerAddressService(addressRepository, userRepository);
    }

    @Test
    void firstAddressBecomesDefaultAndIsReturned() {
        User customer = customer();
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(addressRepository.existsByCustomerIdAndDefaultAddressTrue(10L)).thenReturn(false);
        when(addressRepository.save(any())).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, com.localcart.entity.CustomerAddress.class);
            saved.setId(44L);
            return saved;
        });

        CustomerAddressResponse result = service.addAddress("customer@example.com", request());

        assertEquals(44L, result.getId());
        assertTrue(result.isDefaultAddress());
        assertEquals("Bengaluru", result.getCity());
        verify(addressRepository).save(argThat(address -> address.isDefaultAddress()
                && address.getCustomer().getId().equals(10L)));
    }

    @Test
    void checkoutSnapshotCopiesAddressFieldsAndRestrictsOwnership() {
        User customer = customer();
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        com.localcart.entity.CustomerAddress address = new com.localcart.entity.CustomerAddress();
        address.setId(51L);
        address.setCustomer(customer);
        address.setLabel("Home");
        address.setRecipientName("Test Customer");
        address.setPhone("9876543210");
        address.setAddressLine1("12 Main Road");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPostalCode("560001");
        when(addressRepository.findByIdAndCustomerId(51L, 10L)).thenReturn(Optional.of(address));

        OrderDeliveryAddress snapshot = service.getDeliveryAddressSnapshot("customer@example.com", 51L);

        assertEquals("Home", snapshot.getLabel());
        assertEquals("12 Main Road", snapshot.getAddressLine1());
        assertEquals("560001", snapshot.getPostalCode());
        verify(addressRepository).findByIdAndCustomerId(51L, 10L);
    }

    @Test
    void snapshotRequiresAddressId() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer()));
        assertThrows(com.localcart.exception.BadRequestException.class,
                () -> service.getDeliveryAddressSnapshot("customer@example.com", null));
        verifyNoInteractions(addressRepository);
    }

    private User customer() {
        User customer = new User();
        customer.setName("Test Customer");
        customer.setEmail("customer@example.com");
        customer.setRole(Role.CUSTOMER);
        customer.setActive(true);
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(customer, 10L);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return customer;
    }

    private CustomerAddressRequest request() {
        CustomerAddressRequest request = new CustomerAddressRequest();
        request.setLabel("Home");
        request.setRecipientName("Test Customer");
        request.setPhone("9876543210");
        request.setAddressLine1("12 Main Road");
        request.setCity("Bengaluru");
        request.setState("Karnataka");
        request.setPostalCode("560001");
        return request;
    }
}
