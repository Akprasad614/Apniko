package com.localcart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Delivery details copied to an order when it is placed.
 * This snapshot is independent of the customer's editable address book.
 */
@Embeddable
public class DeliveryAddressSnapshot {

    @Column(name = "delivery_address_label", length = 30)
    private String label;
    @Column(name = "delivery_recipient_name", length = 100)
    private String recipientName;
    @Column(name = "delivery_phone", length = 20)
    private String phone;
    @Column(name = "delivery_address_line1", length = 255)
    private String addressLine1;
    @Column(name = "delivery_address_line2", length = 255)
    private String addressLine2;
    @Column(name = "delivery_landmark", length = 120)
    private String landmark;
    @Column(name = "delivery_locality", length = 120)
    private String locality;
    @Column(name = "delivery_city", length = 80)
    private String city;
    @Column(name = "delivery_state", length = 80)
    private String state;
    @Column(name = "delivery_postal_code", length = 6)
    private String postalCode;

    public DeliveryAddressSnapshot() {
    }

    public static DeliveryAddressSnapshot from(CustomerAddress address) {
        DeliveryAddressSnapshot snapshot = new DeliveryAddressSnapshot();
        snapshot.setLabel(address.getLabel());
        snapshot.setRecipientName(address.getRecipientName());
        snapshot.setPhone(address.getPhone());
        snapshot.setAddressLine1(address.getAddressLine1());
        snapshot.setAddressLine2(address.getAddressLine2());
        snapshot.setLandmark(address.getLandmark());
        snapshot.setLocality(address.getLocality());
        snapshot.setCity(address.getCity());
        snapshot.setState(address.getState());
        snapshot.setPostalCode(address.getPostalCode());
        return snapshot;
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
}
