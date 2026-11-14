package com.localcart.dto;

import com.localcart.entity.CustomerAddress;

public class CustomerAddressResponse {

    private Long id;
    private String label;
    private String recipientName;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String landmark;
    private String city;
    private String state;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private boolean defaultAddress;

    public static CustomerAddressResponse from(CustomerAddress address) {
        CustomerAddressResponse response = new CustomerAddressResponse();
        response.id = address.getId();
        response.label = address.getLabel();
        response.recipientName = address.getRecipientName();
        response.phone = address.getPhone();
        response.addressLine1 = address.getAddressLine1();
        response.addressLine2 = address.getAddressLine2();
        response.landmark = address.getLandmark();
        response.city = address.getCity();
        response.state = address.getState();
        response.postalCode = address.getPostalCode();
        response.latitude = address.getLatitude();
        response.longitude = address.getLongitude();
        response.defaultAddress = address.isDefaultAddress();
        return response;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getLandmark() { return landmark; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public boolean isDefaultAddress() { return defaultAddress; }
}
