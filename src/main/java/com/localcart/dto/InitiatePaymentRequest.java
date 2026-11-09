package com.localcart.dto;

import com.localcart.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public class InitiatePaymentRequest {

    @NotNull(message = "Payment method is required")
    private PaymentMethod method;

    public InitiatePaymentRequest() {
    }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
}
