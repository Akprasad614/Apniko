package com.localcart.service;

import com.localcart.dto.CustomerAddressRequest;
import com.localcart.dto.CustomerAddressResponse;
import com.localcart.entity.CustomerAddress;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.CustomerAddressRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
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
    void firstSavedAddressBecomesDefaultEvenWhenNotRequested() {
        User customer = customer();
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(addressRepository.findAllByCustomerIdOrderByDefaultAddressDescIdDesc(7L))
                .thenReturn(List.of());
        when(addressRepository.save(any(CustomerAddress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerAddressResponse response = service.createAddress(
                "customer@example.com", request(false));

        assertTrue(response.isDefaultAddress());
        verify(addressRepository).save(argThat(CustomerAddress::isDefaultAddress));
    }

    @Test
    void nonCustomerCannotReadAddressBook() {
        User shopkeeper = mock(User.class);
        when(shopkeeper.getId()).thenReturn(9L);
        when(shopkeeper.getRole()).thenReturn(Role.SHOPKEEPER);
        when(shopkeeper.isActive()).thenReturn(true);
        when(userRepository.findByEmail("shopkeeper@example.com"))
                .thenReturn(Optional.of(shopkeeper));

        assertThrows(BadRequestException.class,
                () -> service.getMyAddresses("shopkeeper@example.com"));
        verifyNoInteractions(addressRepository);
    }

    @Test
    void deletingDefaultAddressPromotesAnotherSavedAddress() throws Exception {
        User customer = customer();
        CustomerAddress home = address(21L, true, "Home");
        CustomerAddress work = address(22L, false, "Work");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(addressRepository.findAllByCustomerIdOrderByDefaultAddressDescIdDesc(7L))
                .thenReturn(List.of(home, work));

        service.deleteAddress("customer@example.com", 21L);

        assertTrue(work.isDefaultAddress());
        verify(addressRepository).delete(home);
        verify(addressRepository).save(work);
    }

    private User customer() {
        User customer = mock(User.class);
        when(customer.getId()).thenReturn(7L);
        when(customer.getRole()).thenReturn(Role.CUSTOMER);
        when(customer.isActive()).thenReturn(true);
        return customer;
    }

    private CustomerAddress address(Long id, boolean isDefault, String label) throws Exception {
        CustomerAddress address = new CustomerAddress();
        setId(address, id);
        address.setDefaultAddress(isDefault);
        address.setLabel(label);
        return address;
    }

    private CustomerAddressRequest request(boolean isDefault) {
        CustomerAddressRequest request = new CustomerAddressRequest();
        request.setLabel("Home");
        request.setRecipientName("Customer");
        request.setPhone("+919876543210");
        request.setAddressLine1("12, Market Road");
        request.setLocality("Indiranagar");
        request.setCity("Bengaluru");
        request.setState("Karnataka");
        request.setPostalCode("560038");
        request.setDefaultAddress(isDefault);
        return request;
    }

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
