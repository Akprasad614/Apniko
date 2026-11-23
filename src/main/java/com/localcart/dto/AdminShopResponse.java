package com.localcart.dto;

public class AdminShopResponse {

    private Long id;
    private String name;
    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    private String phone;
    private String address;
    private boolean active;

    public AdminShopResponse(Long id, String name, Long ownerId,
                             String ownerName, String ownerEmail,
                             String phone, String address, boolean active) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.ownerEmail = ownerEmail;
        this.phone = phone;
        this.address = address;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
}
