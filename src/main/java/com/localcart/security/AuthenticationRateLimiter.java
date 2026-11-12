package com.localcart.security;

import com.localcart.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bounded in-memory fixed-window protection for public authentication endpoints.
 * Each request consumes an attempt atomically before authentication is performed.
 */
@Component
public class AuthenticationRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int loginMaximumAttempts;
    private final int registrationMaximumAttempts;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AuthenticationRateLimiter(
            @Value("${security.auth.rate-limit.login.max-attempts:5}") int loginMaximumAttempts,
            @Value("${security.auth.rate-limit.registration.max-attempts:3}") int registrationMaximumAttempts,
            @Value("${security.auth.rate-limit.window-seconds:60}") long windowSeconds) {
        this(loginMaximumAttempts, registrationMaximumAttempts, windowSeconds, Clock.systemUTC());
    }

    AuthenticationRateLimiter(
            int loginMaximumAttempts,
            int registrationMaximumAttempts,
            long windowSeconds,
            Clock clock) {
        if (loginMaximumAttempts < 1 || registrationMaximumAttempts < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Authentication rate-limit settings must be positive");
        }
        this.loginMaximumAttempts = loginMaximumAttempts;
        this.registrationMaximumAttempts = registrationMaximumAttempts;
        this.windowMillis = Math.multiplyExact(windowSeconds, 1000L);
        this.clock = clock;
    }

    /**
     * Consume an attempt for both the client address and normalized account.
     * Counting all attempts (not only failed attempts) prevents concurrent
     * requests and successful logins from resetting or bypassing the limit.
     */
    public void checkLoginAllowed(String clientAddress, String email) {
        consume("login:ip:" + clientAddress, loginMaximumAttempts);
        consume("login:email:" + normalizeEmail(email), loginMaximumAttempts);
    }

    public void checkRegistrationAllowed(String clientAddress) {
        consume("registration:ip:" + clientAddress, registrationMaximumAttempts);
    }

    private void consume(String key, int maximumAttempts) {
        long now = clock.millis();

        if (!windows.containsKey(key) && windows.size() >= MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry ->
                    entry.getValue().isExpired(now, windowMillis));
            if (windows.size() >= MAX_TRACKED_KEYS) {
                throw new TooManyRequestsException(1);
            }
        }

        long[] retryAfterSeconds = {0};
        windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.isExpired(now, windowMillis)) {
                return new Window(now, 1);
            }

            // ConcurrentHashMap.compute serializes updates for this key.
            if (existing.attempts >= maximumAttempts) {
                long remainingMillis = windowMillis - (now - existing.startedAtMillis);
                retryAfterSeconds[0] = Math.max(1, (remainingMillis + 999) / 1000);
                return existing;
            }

            existing.attempts++;
            return existing;
        });

        if (retryAfterSeconds[0] > 0) {
            throw new TooManyRequestsException(retryAfterSeconds[0]);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Window {
        private final long startedAtMillis;
        private int attempts;

        private Window(long startedAtMillis, int attempts) {
            this.startedAtMillis = startedAtMillis;
            this.attempts = attempts;
        }

        private boolean isExpired(long now, long windowMillis) {
            return now - startedAtMillis >= windowMillis;
        }
    }
}
