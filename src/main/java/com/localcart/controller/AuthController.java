package com.localcart.controller;

import com.localcart.dto.AuthResponse;
import com.localcart.dto.LoginRequest;
import com.localcart.dto.RegisterRequest;
import com.localcart.security.AuthenticationRateLimiter;
import com.localcart.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
        rateLimiter.checkRegistrationAllowed(servletRequest.getRemoteAddr());
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.checkLoginAllowed(servletRequest.getRemoteAddr(), request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }
}
