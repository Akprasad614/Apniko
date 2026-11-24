package com.localcart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localcart.exception.BadRequestException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayGatewayClientHttpTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> headers = new ConcurrentHashMap<>();
    private final Map<String, String> requestBodies = new ConcurrentHashMap<>();
    private final Map<String, String> requestMethods = new ConcurrentHashMap<>();
    private volatile int overrideStatus;
    private volatile String overrideBody;
    private HttpServer server;
    private RazorpayGatewayClient gateway;

    @BeforeEach
    void startLocalProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        gateway = new RazorpayGatewayClient("rzp_test_key", "test_secret", baseUrl, objectMapper);
    }

    @AfterEach
    void stopLocalProvider() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        headers.put(key, exchange.getRequestHeaders().getFirst("Authorization"));
        requestMethods.put(key, exchange.getRequestMethod());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requestBodies.put(key, body);

        int status = overrideStatus > 0 ? overrideStatus
                : key.equals("GET /payments/pay_test_12") ? 200
                : key.equals("POST /payments/pay_test_12/refund") ? 200
                : key.equals("POST /orders") ? 200 : 404;
        String response = overrideBody != null ? overrideBody : switch (key) {
            case "POST /orders" -> "{\"id\":\"order_test_42\",\"amount\":12345,\"currency\":\"INR\"}";
            case "GET /payments/pay_test_12" -> "{\"id\":\"pay_test_12\",\"status\":\"captured\"}";
            case "POST /payments/pay_test_12/refund" -> "{\"id\":\"rfnd_test_12\",\"status\":\"processed\"}";
            default -> "{\"error\":\"not found\"}";
        };
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Test
    void createsProviderOrderWithInrAmountNotesAndBasicAuthentication() {
        JsonNode result = gateway.createOrder(12345L, "receipt-42", 42L, 7L);
        String key = "POST /orders";

        assertEquals("order_test_42", result.path("id").asText());
        assertEquals("POST", requestMethods.get(key));
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                "rzp_test_key:test_secret".getBytes(StandardCharsets.UTF_8)),
                headers.get(key));

        JsonNode request = readBody(key);
        assertEquals(12345L, request.path("amount").asLong());
        assertEquals("INR", request.path("currency").asText());
        assertEquals("receipt-42", request.path("receipt").asText());
        assertFalse(request.path("partial_payment").asBoolean());
        assertEquals("42", request.path("notes").path("localcart_order_id").asText());
        assertEquals("7", request.path("notes").path("localcart_payment_id").asText());
    }

    @Test
    void fetchesPaymentAndRequestsFullRefundUsingExpectedEndpoints() {
        assertEquals("pay_test_12", gateway.fetchPayment("pay_test_12").path("id").asText());
        assertEquals("rfnd_test_12", gateway.createFullRefund("pay_test_12", 42L).path("id").asText());

        assertEquals("GET", requestMethods.get("GET /payments/pay_test_12"));
        assertEquals("POST", requestMethods.get("POST /payments/pay_test_12/refund"));
        assertTrue(headers.get("GET /payments/pay_test_12").startsWith("Basic "));
        assertEquals("42", readBody("POST /payments/pay_test_12/refund")
                .path("notes").path("localcart_order_id").asText());
    }

    @Test
    void returnsSanitizedFailureWhenProviderRejectsARequest() {
        overrideStatus = 401;
        overrideBody = "{\"error\":\"sensitive provider payload\"}";

        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> gateway.fetchPayment("pay_bad"));
        assertFalse(failure.getMessage().contains("sensitive provider payload"));
        assertTrue(failure.getMessage().contains("test keys"));
    }

    @Test
    void rejectsNonHttpsRemoteApiBaseUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                new RazorpayGatewayClient("key", "secret", "http://payments.example.com", objectMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new RazorpayGatewayClient("key", "secret", "https://payments.example.com", objectMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new RazorpayGatewayClient("key", "secret", "https://user:pass@api.razorpay.com", objectMapper));
    }

    private JsonNode readBody(String key) {
        try {
            return objectMapper.readTree(requestBodies.getOrDefault(key, "{}"));
        } catch (IOException exception) {
            throw new AssertionError("Provider request body was not valid JSON", exception);
        }
    }
}
