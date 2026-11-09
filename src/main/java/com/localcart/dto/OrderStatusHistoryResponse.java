package com.localcart.dto;

import com.localcart.entity.OrderStatus;
import com.localcart.entity.OrderStatusHistory;

import java.time.LocalDateTime;

public class OrderStatusHistoryResponse {

    private Long id;
    private Long orderId;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private Long changedByUserId;
    private String changedByName;
    private String changedByEmail;
    private String changedByRole;
    private String note;
    private LocalDateTime changedAt;

    public OrderStatusHistoryResponse() {
    }

    public static OrderStatusHistoryResponse from(OrderStatusHistory history) {
        OrderStatusHistoryResponse response = new OrderStatusHistoryResponse();
        response.setId(history.getId());
        response.setOrderId(history.getOrder().getId());
        response.setPreviousStatus(history.getPreviousStatus());
        response.setNewStatus(history.getNewStatus());
        response.setChangedByUserId(history.getChangedByUserId());
        response.setChangedByName(history.getChangedByName());
        response.setChangedByEmail(history.getChangedByEmail());
        response.setChangedByRole(history.getChangedByRole());
        response.setNote(history.getNote());
        response.setChangedAt(history.getChangedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(OrderStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(OrderStatus newStatus) {
        this.newStatus = newStatus;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(Long changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public void setChangedByName(String changedByName) {
        this.changedByName = changedByName;
    }

    public String getChangedByEmail() {
        return changedByEmail;
    }

    public void setChangedByEmail(String changedByEmail) {
        this.changedByEmail = changedByEmail;
    }

    public String getChangedByRole() {
        return changedByRole;
    }

    public void setChangedByRole(String changedByRole) {
        this.changedByRole = changedByRole;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
