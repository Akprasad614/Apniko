package com.localcart.entity;

/** Describes why a shop's inventory quantity changed. */
public enum InventoryMovementType {
    INITIAL_STOCK,
    RESTOCK,
    ADJUSTMENT,
    ORDER_DEDUCTION,
    CANCELLATION_RESTOCK
}
