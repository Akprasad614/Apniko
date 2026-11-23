package com.localcart.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI localCartOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Apniko Backend API")
                        .version("v1")
                        .description("REST API for Apniko grocery marketplace, shop operations, orders, inventory, payments, notifications, analytics and demand insights.")
                        .contact(new Contact()
                                .name("Apniko API Support")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter the JWT access token returned by the Apniko login endpoint. Do not include the word Bearer; Swagger adds it automatically.")));
    }

    @Bean
    public OpenApiCustomizer localCartOperationSecurity() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                boolean publicEndpoint = isPublicEndpoint(path);

                pathItem.readOperations().forEach(operation -> {
                    if (publicEndpoint) {
                        operation.setSecurity(List.of());
                    } else {
                        operation.setSecurity(List.of(
                                new SecurityRequirement().addList(BEARER_AUTH)
                        ));
                    }
                });
            });
        };
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/")
                || "/api/test".equals(path)
                || "/api/payments/webhook/razorpay".equals(path);
    }
}