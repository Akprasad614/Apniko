package com.localcart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localcart.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayGatewayClientCoverageTest {

    @Test
    void trimsConfiguredCredentials() {
        RazorpayGatewayClient client = new RazorpayGatewayClient(
                " rzp_test_key ", " secret ", new ObjectMapper()
        );
        assertTrue(client.isConfigured());
        assertEquals("rzp_test_key", client.getKeyId());
        assertEquals("secret", client.getKeySecret());
    }

    @Test
    void blankCredentialsAreNotConfigured() {
        RazorpayGatewayClient client = new RazorpayGatewayClient(" ", null, new ObjectMapper());
        assertFalse(client.isConfigured());
        assertEquals("", client.getKeyId());
        assertEquals("", client.getKeySecret());
    }

    @Test
    void gatewayOperationsFailFastWhenCredentialsAreMissing() {
        RazorpayGatewayClient client = new RazorpayGatewayClient("", "", new ObjectMapper());
        assertThrows(BadRequestException.class,
                () -> client.createOrder(10000L, "receipt", 1L, 2L));
        assertThrows(BadRequestException.class,
                () -> client.createFullRefund("pay_id", 1L));
        assertThrows(BadRequestException.class,
                () -> client.fetchPayment("pay_id"));
    }

    @Test
    void convertsRupeesToPaiseExactly() {
        assertEquals(12345L, RazorpayGatewayClient.toPaise(new BigDecimal("123.45")));
        assertEquals(100L, RazorpayGatewayClient.toPaise(new BigDecimal("1.00")));
    }

    @Test
    void rejectsAmountsWithMoreThanTwoDecimalPlaces() {
        assertThrows(BadRequestException.class,
                () -> RazorpayGatewayClient.toPaise(new BigDecimal("12.345")));
    }
}
