package com.localcart.dto;

public class ShopResponse {

    private Long id;
    private String name;
    private String ownerName;
    private String phone;
    private String address;
    private boolean active;

    public ShopResponse() {
    }

    public ShopResponse(Long id, String name, String ownerName,
                        String phone, String address, boolean active) {
        this.id = id;
        this.name = name;
        this.ownerName = ownerName;
        this.phone = phone;
        this.address = address;
        this.active = active;
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
}
