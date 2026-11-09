package com.localcart.security;

import com.localcart.service.JwtService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "LocalCart-Phase9-JWT-Test-Secret-Key-At-Least-32-Bytes";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void generatedTokenContainsTheExpectedEmailAndIsValid() {
        String token = jwtService.generateToken("customer@test.com", "CUSTOMER");

        assertNotNull(token);
        assertEquals("customer@test.com", jwtService.extractEmail(token));
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken("customer@test.com", "CUSTOMER");

        assertFalse(jwtService.isTokenValid(token + "tampered"));
    }
}
