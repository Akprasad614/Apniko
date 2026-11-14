package com.localcart.service;

import com.localcart.dto.CustomerAddressRequest;
import com.localcart.dto.CustomerAddressResponse;
import com.localcart.entity.CustomerAddress;
import com.localcart.entity.OrderDeliveryAddress;
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
        return addressRepository.findAllByCustomerIdOrderByIdAsc(customer.getId())
                .stream()
                .map(CustomerAddressResponse::from)
                .toList();
    }

    @Transactional
    public CustomerAddressResponse addAddress(String email, CustomerAddressRequest request) {
        User customer = getCustomer(email);
        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        copyRequest(request, address);

        boolean makeDefault = request.isDefaultAddress()
                || !addressRepository.existsByCustomerIdAndDefaultAddressTrue(customer.getId());
        if (makeDefault) {
            clearDefaultAddress(customer.getId());
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
        CustomerAddress address = getOwnedAddress(customer, addressId);
        copyRequest(request, address);

        if (request.isDefaultAddress()) {
            clearDefaultAddress(customer.getId());
            address.setDefaultAddress(true);
        } else if (!addressRepository.existsByCustomerIdAndDefaultAddressTrue(customer.getId())
                || address.isDefaultAddress()) {
            address.setDefaultAddress(true);
        } else {
            address.setDefaultAddress(false);
        }

        return CustomerAddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public void setDefaultAddress(String email, Long addressId) {
        User customer = getCustomer(email);
        CustomerAddress selected = getOwnedAddress(customer, addressId);
        clearDefaultAddress(customer.getId());
        selected.setDefaultAddress(true);
        addressRepository.save(selected);
    }

    @Transactional
    public void deleteAddress(String email, Long addressId) {
        User customer = getCustomer(email);
        CustomerAddress address = getOwnedAddress(customer, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);

        if (wasDefault) {
            List<CustomerAddress> remaining =
                    addressRepository.findAllByCustomerIdOrderByIdAsc(customer.getId());
            if (!remaining.isEmpty()) {
                CustomerAddress nextDefault = remaining.get(0);
                nextDefault.setDefaultAddress(true);
                addressRepository.save(nextDefault);
            }
        }
    }

    @Transactional(readOnly = true)
    public OrderDeliveryAddress getDeliveryAddressSnapshot(String email, Long addressId) {
        if (addressId == null) {
            throw new BadRequestException("Select a delivery address before placing this order");
        }
        User customer = getCustomer(email);
        CustomerAddress address = getOwnedAddress(customer, addressId);

        OrderDeliveryAddress snapshot = new OrderDeliveryAddress();
        snapshot.setLabel(address.getLabel());
        snapshot.setRecipientName(address.getRecipientName());
        snapshot.setPhone(address.getPhone());
        snapshot.setAddressLine1(address.getAddressLine1());
        snapshot.setAddressLine2(address.getAddressLine2());
        snapshot.setLandmark(address.getLandmark());
        snapshot.setCity(address.getCity());
        snapshot.setState(address.getState());
        snapshot.setPostalCode(address.getPostalCode());
        snapshot.setLatitude(address.getLatitude());
        snapshot.setLongitude(address.getLongitude());
        return snapshot;
    }

    private User getCustomer(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("User is not authenticated");
        }
        User customer = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!customer.isActive() || customer.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("Only an active customer can manage delivery addresses");
        }
        return customer;
    }

    private CustomerAddress getOwnedAddress(User customer, Long addressId) {
        return addressRepository.findByIdAndCustomerId(addressId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery address not found"));
    }

    private void clearDefaultAddress(Long customerId) {
        List<CustomerAddress> addresses =
                addressRepository.findAllByCustomerIdOrderByIdAsc(customerId);
        for (CustomerAddress address : addresses) {
            if (address.isDefaultAddress()) {
                address.setDefaultAddress(false);
                addressRepository.save(address);
            }
        }
    }

    private void copyRequest(CustomerAddressRequest request, CustomerAddress address) {
        address.setLabel(request.getLabel().trim());
        address.setRecipientName(request.getRecipientName().trim());
        address.setPhone(request.getPhone().trim());
        address.setAddressLine1(request.getAddressLine1().trim());
        address.setAddressLine2(cleanOptional(request.getAddressLine2()));
        address.setLandmark(cleanOptional(request.getLandmark()));
        address.setCity(request.getCity().trim());
        address.setState(request.getState().trim());
        address.setPostalCode(request.getPostalCode().trim());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
