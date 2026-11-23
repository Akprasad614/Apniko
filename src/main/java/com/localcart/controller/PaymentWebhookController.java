package com.localcart.controller;

import com.localcart.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/webhook")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<Void> receiveRazorpayWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleRazorpayWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
