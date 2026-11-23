package com.localcart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localcart.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RazorpayGatewayClient {

    private static final String API_BASE_URL = "https://api.razorpay.com/v1";

    private final String keyId;
    private final String keySecret;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RazorpayGatewayClient(
            @Value("${RAZORPAY_KEY_ID:}") String keyId,
            @Value("${RAZORPAY_KEY_SECRET:}") String keySecret,
            ObjectMapper objectMapper) {

        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return !keyId.isBlank() && !keySecret.isBlank();
    }

    public String getKeyId() {
        return keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public JsonNode createOrder(
            long amountInPaise,
            String receipt,
            Long localOrderId,
            Long localPaymentId) {

        requireConfigured();

        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("localcart_order_id", String.valueOf(localOrderId));
        notes.put("localcart_payment_id", String.valueOf(localPaymentId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountInPaise);
        payload.put("currency", "INR");
        payload.put("receipt", receipt);
        payload.put("partial_payment", false);
        payload.put("notes", notes);

        return send("POST", API_BASE_URL + "/orders", payload);
    }

    public JsonNode createFullRefund(
            String razorpayPaymentId,
            Long localOrderId) {
        requireConfigured();

        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("localcart_order_id", String.valueOf(localOrderId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notes", notes);
        return send(
                "POST",
                API_BASE_URL + "/payments/" + razorpayPaymentId + "/refund",
                payload
        );
    }

    public JsonNode fetchPayment(String razorpayPaymentId) {
        requireConfigured();
        return send(
                "GET",
                API_BASE_URL + "/payments/" + razorpayPaymentId,
                null
        );
    }

    private JsonNode send(String method, String url, Object payload) {
        try {
            String credentials = keyId + ":" + keySecret;
            String basicAuth = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8)
            );

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Accept", "application/json");

            if ("POST".equalsIgnoreCase(method)) {
                String json = objectMapper.writeValueAsString(payload);
                requestBuilder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json));
            } else {
                requestBuilder.GET();
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException(
                        "Payment provider request failed. Check the Razorpay test keys and dashboard settings."
                );
            }

            return objectMapper.readTree(response.body());

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException(
                    "Payment provider request was interrupted"
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Unable to communicate with the payment provider"
            );
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BadRequestException(
                    "Online payments are not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET."
            );
        }
    }

    public static long toPaise(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new BadRequestException(
                    "Payment amount must have no more than two decimal places"
            );
        }
    }
}
