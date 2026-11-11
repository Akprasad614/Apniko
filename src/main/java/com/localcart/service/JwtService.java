package com.localcart.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMillis;

    @Autowired
    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds:86400}") long expirationSeconds) {
        this(secret, expirationSeconds, true);
    }

    public JwtService(String secret) {
        this(secret, 86400, true);
    }

    public static JwtService forTesting(String secret, long expirationSeconds) {
        return new JwtService(secret, expirationSeconds, true);
    }

    private JwtService(String secret, long expirationSeconds, boolean validated) {
        if (expirationSeconds < 1) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationMillis = Math.multiplyExact(expirationSeconds, 1000L);
    }

    public String generateToken(String email, String role) {

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(
                        new Date(System.currentTimeMillis()
                                + expirationMillis)
                )
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {

        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {

        try {
            Claims claims = getClaims(token);

            return claims.getExpiration().after(new Date());

        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
