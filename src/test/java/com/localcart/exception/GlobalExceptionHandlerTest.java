package com.localcart.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundExceptionReturns404WithMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNotFound(new ResourceNotFoundException("Shop not found"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("Shop not found", response.getBody().get("error"));
    }

    @Test
    void badRequestExceptionReturns400WithMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleBadRequest(new BadRequestException("Invalid quantity"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid quantity", response.getBody().get("error"));
    }

    @Test
    void authenticationFailureDoesNotExposeCredentialDetails() {
        ResponseEntity<Map<String, String>> response =
                handler.handleAuthentication(new BadCredentialsException("secret detail"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Invalid email or password", response.getBody().get("error"));
        assertFalse(response.getBody().get("error").contains("secret detail"));
    }

    @Test
    void rateLimitFailureReturns429WithRetryAfterHeader() {
        ResponseEntity<Map<String, String>> response = handler.handleTooManyRequests(
                new TooManyRequestsException(42)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("42", response.getHeaders().getFirst("Retry-After"));
        assertEquals("Too many requests. Please try again later.", response.getBody().get("error"));
    }

    @Test
    void accessDeniedExceptionReturns403() {
        ResponseEntity<Map<String, String>> response =
                handler.handleAccessDenied(new AccessDeniedException("Forbidden"));

        assertEquals(403, response.getStatusCode().value());
        assertEquals("Forbidden", response.getBody().get("error"));
    }

    @Test
    void unexpectedExceptionDoesNotLeakInternalDetails() {
        ResponseEntity<Map<String, String>> response =
                handler.handleGeneral(new IllegalStateException("database password leaked?"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Something went wrong", response.getBody().get("error"));
    }
}
