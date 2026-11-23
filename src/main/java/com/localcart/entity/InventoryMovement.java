package com.localcart.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_movements", indexes = {
        @Index(name = "idx_inventory_movement_inventory_date",
                columnList = "inventory_id, created_at"),
        @Index(name = "idx_inventory_movement_shop_date",
                columnList = "shop_id, created_at")
})
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private ShopInventory inventory;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 180)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40)
    private InventoryMovementType movementType;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "previous_quantity", nullable = false)
    private int previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private int newQuantity;

    @Column(name = "performed_by_user_id")
    private Long performedByUserId;

    @Column(name = "performed_by_name", length = 120)
    private String performedByName;

    @Column(name = "performed_by_role", length = 40)
    private String performedByRole;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public InventoryMovement() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public ShopInventory getInventory() {
        return inventory;
    }

    public void setInventory(ShopInventory inventory) {
        this.inventory = inventory;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public InventoryMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(InventoryMovementType movementType) {
        this.movementType = movementType;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public int getPreviousQuantity() {
        return previousQuantity;
    }

    public void setPreviousQuantity(int previousQuantity) {
        this.previousQuantity = previousQuantity;
    }

    public int getNewQuantity() {
        return newQuantity;
    }

    public void setNewQuantity(int newQuantity) {
        this.newQuantity = newQuantity;
    }

    public Long getPerformedByUserId() {
        return performedByUserId;
    }

    public void setPerformedByUserId(Long performedByUserId) {
        this.performedByUserId = performedByUserId;
    }

    public String getPerformedByName() {
        return performedByName;
    }

    public void setPerformedByName(String performedByName) {
        this.performedByName = performedByName;
    }

    public String getPerformedByRole() {
        return performedByRole;
    }

    public void setPerformedByRole(String performedByRole) {
        this.performedByRole = performedByRole;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
