package com.localcart.service;

import com.localcart.dto.AuthResponse;
import com.localcart.dto.LoginRequest;
import com.localcart.dto.RegisterRequest;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwtService = mock(JwtService.class);
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtService
        );
    }

    @Test
    void registerCreatesAnActiveCustomerAndReturnsToken() {
        RegisterRequest request = new RegisterRequest();
        request.setName("  Priya Sharma  ");
        request.setEmail("priya@example.com");
        request.setPassword("StrongPass123");
        request.setRole(Role.CUSTOMER);

        when(userRepository.existsByEmail("priya@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken("priya@example.com", "CUSTOMER"))
                .thenReturn("test.jwt.token");

        AuthResponse response = authService.register(request);

        assertEquals("test.jwt.token", response.getToken());
        assertEquals("priya@example.com", response.getEmail());
        assertEquals("Priya Sharma", response.getName());
        assertEquals("CUSTOMER", response.getRole());

        verify(userRepository).save(argThat(user ->
                "Priya Sharma".equals(user.getName())
                        && "priya@example.com".equals(user.getEmail())
                        && "encoded-password".equals(user.getPassword())
                        && user.getRole() == Role.CUSTOMER
                        && user.isActive()
        ));
    }

    @Test
    void registerRejectsAnEmailThatAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Priya");
        request.setEmail("priya@example.com");
        request.setPassword("StrongPass123");
        request.setRole(Role.CUSTOMER);
        when(userRepository.existsByEmail("priya@example.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    void publicRegistrationRejectsPrivilegedRoles() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Mallory");
        request.setEmail("mallory@example.com");
        request.setPassword("StrongPass123");
        request.setRole(Role.ADMIN);
        when(userRepository.existsByEmail("mallory@example.com")).thenReturn(false);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authService.register(request)
        );

        assertTrue(exception.getMessage().contains("only allowed for CUSTOMER"));
        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(any(String.class), any(String.class));
    }

    @Test
    void loginAuthenticatesUserAndReturnsJwt() {
        LoginRequest request = new LoginRequest();
        request.setEmail("priya@example.com");
        request.setPassword("StrongPass123");

        User user = new User();
        user.setName("Priya Sharma");
        user.setEmail("priya@example.com");
        user.setRole(Role.CUSTOMER);
        user.setActive(true);

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("priya@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtService.generateToken("priya@example.com", "CUSTOMER"))
                .thenReturn("login.jwt.token");

        AuthResponse response = authService.login(request);

        assertEquals("login.jwt.token", response.getToken());
        assertEquals("priya@example.com", response.getEmail());
        assertEquals("CUSTOMER", response.getRole());
        verify(authenticationManager).authenticate(any(Authentication.class));
    }

    @Test
    void loginDoesNotIssueTokenWhenUserRecordIsMissing() {
        LoginRequest request = new LoginRequest();
        request.setEmail("missing@example.com");
        request.setPassword("StrongPass123");
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(any(String.class), any(String.class));
    }
}
