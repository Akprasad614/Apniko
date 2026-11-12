package com.localcart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerAddressRequest {

    @NotBlank(message = "Address label is required")
    @Size(max = 30, message = "Address label must be 30 characters or fewer")
    private String label;

    @NotBlank(message = "Recipient name is required")
    @Size(max = 100, message = "Recipient name must be 100 characters or fewer")
    private String recipientName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,19}$", message = "Enter a valid phone number")
    private String phone;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 must be 255 characters or fewer")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 must be 255 characters or fewer")
    private String addressLine2;

    @Size(max = 120, message = "Landmark must be 120 characters or fewer")
    private String landmark;

    @NotBlank(message = "Area or locality is required")
    @Size(max = 120, message = "Locality must be 120 characters or fewer")
    private String locality;

    @NotBlank(message = "City is required")
    @Size(max = 80, message = "City must be 80 characters or fewer")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 80, message = "State must be 80 characters or fewer")
    private String state;

    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit Indian PIN code")
    private String postalCode;

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
    public String getLocality() { return locality; }
    public void setLocality(String locality) { this.locality = locality; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
