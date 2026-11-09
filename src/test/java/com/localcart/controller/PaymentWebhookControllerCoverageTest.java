package com.localcart.controller;

import com.localcart.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentWebhookControllerCoverageTest {

    private PaymentService paymentService;
    private PaymentWebhookController controller;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        controller = new PaymentWebhookController(paymentService);
    }

    @Test
    void forwardsRawPayloadAndSignatureThenReturnsOk() {
        String payload = "{\"event\":\"payment.captured\"}";
        String signature = "test-signature";

        var response = controller.receiveRazorpayWebhook(payload, signature);

        assertEquals(200, response.getStatusCode().value());
        verify(paymentService).handleRazorpayWebhook(payload, signature);
    }

    @Test
    void forwardsMissingSignatureForServiceLevelRejection() {
        String payload = "{}";

        var response = controller.receiveRazorpayWebhook(payload, null);

        assertEquals(200, response.getStatusCode().value());
        verify(paymentService).handleRazorpayWebhook(payload, null);
    }
}
