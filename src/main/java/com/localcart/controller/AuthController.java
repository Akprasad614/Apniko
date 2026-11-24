package com.localcart.controller;

import com.localcart.dto.AuthResponse;
import com.localcart.dto.LoginRequest;
import com.localcart.dto.RegisterRequest;
import com.localcart.security.AuthenticationRateLimiter;
import com.localcart.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationRateLimiter rateLimiter;

    @Value("${app.auth.cookie.enabled:true}")
    private boolean cookieAuthenticationEnabled;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${app.auth.cookie.name:APNIKO_SESSION}")
    private String cookieName;

    @Value("${jwt.expiration-ms:86400000}")
    private long tokenExpirationMs;

    public AuthController(AuthService authService, AuthenticationRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("token", csrfToken.getToken()));
    }

    @GetMapping("/session")
    public ResponseEntity<AuthResponse> session(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.currentSession(authentication.getName()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.checkRegistrationAllowed(servletRequest.getRemoteAddr());
        return createAuthenticatedResponse(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.checkLoginAllowed(servletRequest.getRemoteAddr(), request.getEmail());
        return createAuthenticatedResponse(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        if (!cookieAuthenticationEnabled) {
            return ResponseEntity.noContent().build();
        }

        ResponseCookie expiredCookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    private ResponseEntity<AuthResponse> createAuthenticatedResponse(AuthResponse response) {
        if (!cookieAuthenticationEnabled) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
        }

        ResponseCookie sessionCookie = ResponseCookie.from(cookieName, response.getToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(tokenExpirationMs))
                .build();

        AuthResponse safeResponse = new AuthResponse(
                null,
                response.getUserId(),
                response.getName(),
                response.getEmail(),
                response.getRole()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
                .body(safeResponse);
    }
}
