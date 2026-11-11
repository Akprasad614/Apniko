package com.localcart.security;

import com.localcart.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory fixed-window protection for public authentication endpoints. */
@Component
public class AuthenticationRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;
    private final int loginMaximumAttempts;
    private final int registrationMaximumAttempts;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

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

    public void checkLoginAllowed(String clientAddress, String email) {
        check("login:ip:" + clientAddress, loginMaximumAttempts);
        check("login:email:" + normalizeEmail(email), loginMaximumAttempts);
    }

    public void recordLoginFailure(String clientAddress, String email) {
        increment("login:ip:" + clientAddress);
        increment("login:email:" + normalizeEmail(email));
    }

    public void recordLoginSuccess(String clientAddress, String email) {
        windows.remove("login:ip:" + clientAddress);
        windows.remove("login:email:" + normalizeEmail(email));
    }

    public void checkRegistrationAllowed(String clientAddress) {
        check("registration:ip:" + clientAddress, registrationMaximumAttempts);
    }

    public void recordRegistrationAttempt(String clientAddress) {
        increment("registration:ip:" + clientAddress);
    }

    private void check(String key, int maximumAttempts) {
        Window window = windows.get(key);
        if (window == null) {
            return;
        }
        synchronized (window) {
            long now = clock.millis();
            if (window.isExpired(now, windowMillis)) {
                windows.remove(key, window);
                return;
            }
            if (window.attempts >= maximumAttempts) {
                long remainingMillis = windowMillis - (now - window.startedAtMillis);
                throw new TooManyRequestsException(Math.max(1, (remainingMillis + 999) / 1000));
            }
        }
    }

    private void increment(String key) {
        long now = clock.millis();
        if (!windows.containsKey(key) && windows.size() >= MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMillis));
            if (windows.size() >= MAX_TRACKED_KEYS) {
                throw new TooManyRequestsException(1);
            }
        }
        windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.isExpired(now, windowMillis)) {
                return new Window(now, 1);
            }
            existing.attempts++;
            return existing;
        });
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
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
