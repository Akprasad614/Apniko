package com.localcart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Jackson 2 ObjectMapper used by RazorpayGatewayClient.
 * Spring Boot 4 configures Jackson 3 (tools.jackson) by default, while
 * this integration currently uses Jackson 2 (com.fasterxml.jackson).
 */
@Configuration
public class Jackson2CompatibilityConfig {

    @Bean(name = "razorpayJackson2ObjectMapper")
    public ObjectMapper razorpayJackson2ObjectMapper() {
        return new ObjectMapper();
    }
}
