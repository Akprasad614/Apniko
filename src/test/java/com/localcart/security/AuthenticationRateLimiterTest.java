package com.localcart.security;

import com.localcart.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationRateLimiterTest {

    @Test
    void blocksLoginAfterConfiguredAttemptsAndReturnsRetryInformation() {
        AuthenticationRateLimiter limiter =
                new AuthenticationRateLimiter(2, 3, 60, java.time.Clock.systemUTC());

        limiter.checkLoginAllowed("127.0.0.1", "customer@example.com");
        limiter.checkLoginAllowed("127.0.0.1", "customer@example.com");

        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                () -> limiter.checkLoginAllowed("127.0.0.1", "customer@example.com")
        );
        assertTrue(exception.getRetryAfterSeconds() > 0);
    }

    @Test
    void loginLimitAppliesAcrossDifferentAccountsFromTheSameAddress() {
        AuthenticationRateLimiter limiter =
                new AuthenticationRateLimiter(2, 3, 60, java.time.Clock.systemUTC());

        limiter.checkLoginAllowed("127.0.0.1", "first@example.com");
        limiter.checkLoginAllowed("127.0.0.1", "second@example.com");

        assertThrows(TooManyRequestsException.class,
                () -> limiter.checkLoginAllowed("127.0.0.1", "third@example.com"));
    }

    @Test
    void loginLimitAppliesToTheSameAccountAcrossDifferentAddresses() {
        AuthenticationRateLimiter limiter =
                new AuthenticationRateLimiter(2, 3, 60, java.time.Clock.systemUTC());

        limiter.checkLoginAllowed("192.0.2.10", "customer@example.com");
        limiter.checkLoginAllowed("192.0.2.11", "customer@example.com");

        assertThrows(TooManyRequestsException.class,
                () -> limiter.checkLoginAllowed("192.0.2.12", "customer@example.com"));
    }

    @Test
    void blocksRegistrationAfterConfiguredAttempts() {
        AuthenticationRateLimiter limiter =
                new AuthenticationRateLimiter(5, 2, 60, java.time.Clock.systemUTC());

        limiter.checkRegistrationAllowed("127.0.0.1");
        limiter.checkRegistrationAllowed("127.0.0.1");

        assertThrows(TooManyRequestsException.class,
                () -> limiter.checkRegistrationAllowed("127.0.0.1"));
    }

    @Test
    void permitsAttemptsForDifferentAddressesWithinTheirOwnWindows() {
        AuthenticationRateLimiter limiter =
                new AuthenticationRateLimiter(1, 1, 60, java.time.Clock.systemUTC());

        limiter.checkLoginAllowed("192.0.2.20", "first@example.com");
        assertDoesNotThrow(() ->
                limiter.checkLoginAllowed("192.0.2.21", "second@example.com"));
    }
}
