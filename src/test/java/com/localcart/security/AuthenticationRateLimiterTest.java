package com.localcart.security;

import com.localcart.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationRateLimiterTest {

    @Test
    void blocksLoginAfterConfiguredFailuresByEmailAndReturnsRetryInformation() {
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(2, 3, 60, java.time.Clock.systemUTC());

        limiter.recordLoginFailure("127.0.0.1", "customer@example.com");
        limiter.recordLoginFailure("127.0.0.1", "customer@example.com");

        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> limiter.checkLoginAllowed("127.0.0.1", "customer@example.com"));
        assertTruePositive(exception.getRetryAfterSeconds());
    }

    @Test
    void successfulLoginClearsTheFailureWindow() {
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(1, 3, 60, java.time.Clock.systemUTC());

        limiter.recordLoginFailure("127.0.0.1", "customer@example.com");
        limiter.recordLoginSuccess("127.0.0.1", "customer@example.com");

        assertDoesNotThrow(() -> limiter.checkLoginAllowed("127.0.0.1", "customer@example.com"));
    }

    @Test
    void blocksRegistrationAfterConfiguredAttempts() {
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(5, 2, 60, java.time.Clock.systemUTC());
        limiter.recordRegistrationAttempt("127.0.0.1");
        limiter.recordRegistrationAttempt("127.0.0.1");

        assertThrows(TooManyRequestsException.class,
                () -> limiter.checkRegistrationAllowed("127.0.0.1"));
    }

    private static void assertTruePositive(long value) {
        assertEquals(true, value > 0);
    }
}
