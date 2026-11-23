package com.localcart.dto;

import com.localcart.entity.Payment;
import com.localcart.entity.PaymentMethod;
import com.localcart.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {

    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;
    private PaymentStatus status;
    private String razorpayKeyId;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpayRefundId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;

    public static PaymentResponse from(Payment payment, String keyId) {
        PaymentResponse response = new PaymentResponse();
        response.id = payment.getId();
        response.orderId = payment.getOrder().getId();
        response.amount = payment.getAmount();
        response.currency = payment.getCurrency();
        response.method = payment.getMethod();
        response.status = payment.getStatus();
        response.razorpayKeyId = keyId;
        response.razorpayOrderId = payment.getGatewayOrderId();
        response.razorpayPaymentId = payment.getGatewayPaymentId();
        response.razorpayRefundId = payment.getGatewayRefundId();
        response.createdAt = payment.getCreatedAt();
        response.updatedAt = payment.getUpdatedAt();
        response.paidAt = payment.getPaidAt();
        return response;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getRazorpayKeyId() { return razorpayKeyId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public String getRazorpayRefundId() { return razorpayRefundId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
