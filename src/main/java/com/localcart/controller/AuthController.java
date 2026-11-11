package com.localcart.controller;

import com.localcart.dto.AuthResponse;
import com.localcart.dto.LoginRequest;
import com.localcart.dto.RegisterRequest;
import com.localcart.service.AuthService;
import com.localcart.security.AuthenticationRateLimiter;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationRateLimiter rateLimiter;

    public AuthController(AuthService authService, AuthenticationRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        String clientAddress = servletRequest.getRemoteAddr();
        rateLimiter.checkRegistrationAllowed(clientAddress);
        rateLimiter.recordRegistrationAttempt(clientAddress);

        return ResponseEntity.ok(
                authService.register(request)
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        String clientAddress = servletRequest.getRemoteAddr();
        rateLimiter.checkLoginAllowed(clientAddress, request.getEmail());
        try {
            AuthResponse response = authService.login(request);
            rateLimiter.recordLoginSuccess(clientAddress, request.getEmail());
            return ResponseEntity.ok(response);
        } catch (AuthenticationException exception) {
            rateLimiter.recordLoginFailure(clientAddress, request.getEmail());
            throw exception;
        }

    }
}
