package com.localcart.controller;

import com.localcart.dto.AuthResponse;
import com.localcart.dto.LoginRequest;
import com.localcart.service.AuthService;
import com.localcart.security.AuthenticationRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerCookieTest {

    private AuthService authService;
    private AuthenticationRateLimiter rateLimiter;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        rateLimiter = mock(AuthenticationRateLimiter.class);
        controller = new AuthController(authService, rateLimiter);
        ReflectionTestUtils.setField(controller, "cookieAuthenticationEnabled", true);
        ReflectionTestUtils.setField(controller, "secureCookie", true);
        ReflectionTestUtils.setField(controller, "cookieName", "APNIKO_SESSION");
        ReflectionTestUtils.setField(controller, "tokenExpirationMs", 3_600_000L);
    }

    @Test
    void loginReturnsUserDetailsButKeepsJwtInSecureHttpOnlyCookie() {
        LoginRequest request = new LoginRequest();
        request.setEmail("customer@example.test");
        request.setPassword("not-used");
        when(authService.login(request)).thenReturn(
                new AuthResponse("signed.jwt.value", 17L, "Test Customer",
                        "customer@example.test", "CUSTOMER")
        );

        var response = controller.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getToken());
        assertEquals(17L, response.getBody().getUserId());
        assertEquals("CUSTOMER", response.getBody().getRole());

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("APNIKO_SESSION=signed.jwt.value"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=3600"));
    }

    @Test
    void logoutExpiresTheBrowserSessionCookie() {
        var response = controller.logout();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("APNIKO_SESSION="));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Max-Age=0"));
    }

    @Test
    void bearerCompatibilityModeKeepsTheExistingTokenResponse() {
        ReflectionTestUtils.setField(controller, "cookieAuthenticationEnabled", false);
        LoginRequest request = new LoginRequest();
        when(authService.login(request)).thenReturn(
                new AuthResponse("test-only.jwt", 3L, "CI Customer",
                        "ci@example.test", "CUSTOMER")
        );

        var response = controller.login(request);

        assertEquals("test-only.jwt", response.getBody().getToken());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    }
}
