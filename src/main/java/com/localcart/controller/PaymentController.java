package com.localcart.controller;

import com.localcart.dto.InitiatePaymentRequest;
import com.localcart.dto.PaymentResponse;
import com.localcart.dto.VerifyPaymentRequest;
import com.localcart.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/{orderId}/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @PathVariable Long orderId,
            @Valid @RequestBody InitiatePaymentRequest request) {
        return ResponseEntity.ok(
                paymentService.initiatePayment(orderId, request)
        );
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok(
                paymentService.verifyPayment(request)
        );
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentForOrder(
            @PathVariable Long orderId) {
        return ResponseEntity.ok(
                paymentService.getPaymentForOrder(orderId)
        );
    }

    @PostMapping("/{paymentId}/collect-cod")
    public ResponseEntity<PaymentResponse> collectCashOnDelivery(
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(
                paymentService.collectCashOnDelivery(paymentId)
        );
    }

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(
                paymentService.refundPaymentByAdmin(paymentId)
        );
    }

    @PostMapping("/{paymentId}/confirm-cod-refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> confirmCodRefund(
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(
                paymentService.confirmCodRefundByAdmin(paymentId)
        );
    }

}
