package com.localcart.dto;

import jakarta.validation.constraints.*;

public class CustomerAddressRequest {

    @NotBlank
    @Size(max = 40)
    private String label;

    @NotBlank
    @Size(max = 100)
    private String recipientName;

    @NotBlank
    @Pattern(regexp = "^\\+?[0-9][0-9\\s-]{7,19}$", message = "Enter a valid phone number")
    private String phone;

    @NotBlank
    @Size(max = 180)
    private String addressLine1;

    @Size(max = 180)
    private String addressLine2;

    @Size(max = 120)
    private String landmark;

    @NotBlank
    @Size(max = 80)
    private String city;

    @NotBlank
    @Size(max = 80)
    private String state;

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit PIN code")
    private String postalCode;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    private boolean defaultAddress;

    public CustomerAddressRequest() {
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
