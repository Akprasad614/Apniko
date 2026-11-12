package com.localcart.service;

import com.localcart.dto.CustomerAddressRequest;
import com.localcart.dto.CustomerAddressResponse;
import com.localcart.entity.CustomerAddress;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.CustomerAddressRepository;
import com.localcart.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;

    public CustomerAddressService(
            CustomerAddressRepository addressRepository,
            UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> getMyAddresses(String email) {
        User customer = getCustomer(email);
        return addressRepository
                .findAllByCustomerIdOrderByDefaultAddressDescIdDesc(customer.getId())
                .stream()
                .map(CustomerAddressResponse::from)
                .toList();
    }

    @Transactional
    public CustomerAddressResponse createAddress(
            String email,
            CustomerAddressRequest request) {
        User customer = getCustomer(email);
        List<CustomerAddress> current = findAddresses(customer.getId());

        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        copyRequest(request, address);

        boolean hasDefault = current.stream().anyMatch(CustomerAddress::isDefaultAddress);
        boolean makeDefault = request.isDefaultAddress() || current.isEmpty() || !hasDefault;
        if (makeDefault) {
            current.forEach(existing -> existing.setDefaultAddress(false));
            addressRepository.saveAll(current);
        }
        address.setDefaultAddress(makeDefault);

        return CustomerAddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public CustomerAddressResponse updateAddress(
            String email,
            Long addressId,
            CustomerAddressRequest request) {
        User customer = getCustomer(email);
        List<CustomerAddress> current = findAddresses(customer.getId());
        CustomerAddress address = current.stream()
                .filter(item -> item.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        boolean wasDefault = address.isDefaultAddress();
        copyRequest(request, address);

        if (request.isDefaultAddress()) {
            current.forEach(item -> item.setDefaultAddress(false));
            address.setDefaultAddress(true);
        } else if (wasDefault) {
            CustomerAddress replacement = current.stream()
                    .filter(item -> !item.getId().equals(addressId))
                    .findFirst()
                    .orElse(null);
            if (replacement == null) {
                address.setDefaultAddress(true);
            } else {
                address.setDefaultAddress(false);
                replacement.setDefaultAddress(true);
            }
        } else {
            address.setDefaultAddress(false);
        }

        addressRepository.saveAll(current);
        return CustomerAddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public CustomerAddressResponse makeDefault(String email, Long addressId) {
        User customer = getCustomer(email);
        List<CustomerAddress> current = findAddresses(customer.getId());
        CustomerAddress selected = current.stream()
                .filter(item -> item.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        current.forEach(address ->
                address.setDefaultAddress(address.getId().equals(addressId)));
        addressRepository.saveAll(current);
        return CustomerAddressResponse.from(selected);
    }

    @Transactional
    public void deleteAddress(String email, Long addressId) {
        User customer = getCustomer(email);
        List<CustomerAddress> current = findAddresses(customer.getId());
        CustomerAddress selected = current.stream()
                .filter(address -> address.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        boolean wasDefault = selected.isDefaultAddress();
        addressRepository.delete(selected);

        if (wasDefault) {
            current.stream()
                    .filter(address -> !address.getId().equals(addressId))
                    .findFirst()
                    .ifPresent(replacement -> {
                        replacement.setDefaultAddress(true);
                        addressRepository.save(replacement);
                    });
        }
    }

    private List<CustomerAddress> findAddresses(Long customerId) {
        return addressRepository
                .findAllByCustomerIdOrderByDefaultAddressDescIdDesc(customerId);
    }

    private User getCustomer(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("User is not authenticated");
        }

        User customer = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (customer.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("Only customers can manage delivery addresses");
        }
        if (!customer.isActive()) {
            throw new BadRequestException("Customer account is inactive");
        }
        return customer;
    }

    private void copyRequest(CustomerAddressRequest request, CustomerAddress address) {
        address.setLabel(request.getLabel().trim());
        address.setRecipientName(request.getRecipientName().trim());
        address.setPhone(request.getPhone().trim());
        address.setAddressLine1(request.getAddressLine1().trim());
        address.setAddressLine2(blankToNull(request.getAddressLine2()));
        address.setLandmark(blankToNull(request.getLandmark()));
        address.setLocality(request.getLocality().trim());
        address.setCity(request.getCity().trim());
        address.setState(request.getState().trim());
        address.setPostalCode(request.getPostalCode().trim());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
