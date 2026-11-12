package com.localcart.service;

import com.localcart.dto.AuthResponse;
import com.localcart.dto.LoginRequest;
import com.localcart.dto.RegisterRequest;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Public registration is intentionally limited to CUSTOMER accounts. */
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            // Do not use registration responses to confirm whether an account exists.
            throw new BadRequestException("Unable to register with the provided details");
        }

        if (request.getRole() != Role.CUSTOMER) {
            throw new BadRequestException(
                    "Public registration is only allowed for CUSTOMER accounts"
            );
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // Never trust a client-supplied role as an authority source.
        user.setRole(Role.CUSTOMER);
        user.setActive(true);

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(
                savedUser.getEmail(), savedUser.getRole().name()
        );

        return new AuthResponse(
                token,
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail, request.getPassword()
                )
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!user.isActive()) {
            throw new BadRequestException("Invalid email or password");
        }

        String token = jwtService.generateToken(
                user.getEmail(), user.getRole().name()
        );

        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
