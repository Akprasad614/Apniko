package com.localcart.controller;

import com.localcart.dto.CustomerAddressRequest;
import com.localcart.dto.CustomerAddressResponse;
import com.localcart.security.SecurityUtils;
import com.localcart.service.CustomerAddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerAddressController {

    private final CustomerAddressService addressService;

    public CustomerAddressController(CustomerAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<List<CustomerAddressResponse>> getMyAddresses() {
        return ResponseEntity.ok(addressService.getMyAddresses(SecurityUtils.getCurrentUserEmail()));
    }

    @PostMapping
    public ResponseEntity<CustomerAddressResponse> addAddress(
            @Valid @RequestBody CustomerAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.addAddress(SecurityUtils.getCurrentUserEmail(), request));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<CustomerAddressResponse> updateAddress(
            @PathVariable Long addressId,
            @Valid @RequestBody CustomerAddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(
                SecurityUtils.getCurrentUserEmail(), addressId, request));
    }

    @PutMapping("/{addressId}/default")
    public ResponseEntity<Void> setDefaultAddress(@PathVariable Long addressId) {
        addressService.setDefaultAddress(SecurityUtils.getCurrentUserEmail(), addressId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long addressId) {
        addressService.deleteAddress(SecurityUtils.getCurrentUserEmail(), addressId);
        return ResponseEntity.noContent().build();
    }
}
