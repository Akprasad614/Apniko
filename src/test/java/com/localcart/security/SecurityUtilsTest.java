package com.localcart.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsEmailForAnAuthenticatedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "customer@test.com",
                        "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                )
        );

        assertEquals("customer@test.com", SecurityUtils.getCurrentUserEmail());
    }

    @Test
    void returnsNullWhenThereIsNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertNull(SecurityUtils.getCurrentUserEmail());
    }

    @Test
    void returnsNullForAnUnauthenticatedToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", "credentials")
        );

        assertNull(SecurityUtils.getCurrentUserEmail());
    }
}
