package com.localcart.dto;

import com.localcart.entity.DeliveryMode;

public class ShopResponse {

    private Long id;
    private String name;
    private String ownerName;
    private String phone;
    private String address;
    private boolean active;
    private DeliveryMode deliveryMode = DeliveryMode.SHOP_DELIVERY;
    private Double latitude;
    private Double longitude;

    public ShopResponse() {
    }

    public ShopResponse(Long id, String name, String ownerName,
                        String phone, String address, boolean active) {
        this(id, name, ownerName, phone, address, active, DeliveryMode.SHOP_DELIVERY);
    }

    public ShopResponse(Long id, String name, String ownerName,
                        String phone, String address, boolean active,
                        DeliveryMode deliveryMode) {
        this(id, name, ownerName, phone, address, active, deliveryMode, null, null);
    }

    public ShopResponse(Long id, String name, String ownerName,
                        String phone, String address, boolean active,
                        DeliveryMode deliveryMode, Double latitude, Double longitude) {
        this.id = id;
        this.name = name;
        this.ownerName = ownerName;
        this.phone = phone;
        this.address = address;
        this.active = active;
        this.deliveryMode = deliveryMode == null ? DeliveryMode.SHOP_DELIVERY : deliveryMode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public DeliveryMode getDeliveryMode() {
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode == null ? DeliveryMode.SHOP_DELIVERY : deliveryMode;
    }
}
