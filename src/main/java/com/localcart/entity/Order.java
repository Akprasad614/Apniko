package com.localcart.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private User customer;

    @ManyToOne
    @JoinColumn(name = "delivery_partner_id")
    @JsonIgnore
    private User deliveryPartner;

    private double totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    private DeliveryMode deliveryMode;

    @Embedded
    private OrderDeliveryAddress deliveryAddress;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL
    )
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Shop getShop() {
        return shop;
    }

    public void setShop(Shop shop) {
        this.shop = shop;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public User getDeliveryPartner() {
        return deliveryPartner;
    }

    public void setDeliveryPartner(User deliveryPartner) {
        this.deliveryPartner = deliveryPartner;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public OrderDeliveryAddress getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(OrderDeliveryAddress deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}