package com.localcart.dto;

import com.localcart.entity.InventoryMovement;

import java.time.LocalDateTime;

public class InventoryMovementResponse {

    private Long id;
    private Long inventoryId;
    private Long shopId;
    private Long productId;
    private String productName;
    private String movementType;
    private int quantityChange;
    private int previousQuantity;
    private int newQuantity;
    private Long performedByUserId;
    private String performedByName;
    private String performedByRole;
    private String note;
    private LocalDateTime createdAt;

    public InventoryMovementResponse() {
    }

    public static InventoryMovementResponse from(InventoryMovement movement) {
        InventoryMovementResponse response = new InventoryMovementResponse();
        response.id = movement.getId();
        response.inventoryId = movement.getInventory().getId();
        response.shopId = movement.getShopId();
        response.productId = movement.getProductId();
        response.productName = movement.getProductName();
        response.movementType = movement.getMovementType().name();
        response.quantityChange = movement.getQuantityChange();
        response.previousQuantity = movement.getPreviousQuantity();
        response.newQuantity = movement.getNewQuantity();
        response.performedByUserId = movement.getPerformedByUserId();
        response.performedByName = movement.getPerformedByName();
        response.performedByRole = movement.getPerformedByRole();
        response.note = movement.getNote();
        response.createdAt = movement.getCreatedAt();
        return response;
    }

    public Long getId() { return id; }
    public Long getInventoryId() { return inventoryId; }
    public Long getShopId() { return shopId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getMovementType() { return movementType; }
    public int getQuantityChange() { return quantityChange; }
    public int getPreviousQuantity() { return previousQuantity; }
    public int getNewQuantity() { return newQuantity; }
    public Long getPerformedByUserId() { return performedByUserId; }
    public String getPerformedByName() { return performedByName; }
    public String getPerformedByRole() { return performedByRole; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
