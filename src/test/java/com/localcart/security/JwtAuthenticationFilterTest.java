package com.localcart.security;

import com.localcart.service.CustomUserDetailsService;
import com.localcart.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = mock(JwtService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestWithoutBearerTokenContinuesWithoutLoadingAUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validTokenAuthenticatesAnEnabledUser() throws Exception {
        String token = "valid.jwt.token";
        String email = "customer@test.com";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        UserDetails details = User.withUsername(email)
                .password("not-used")
                .roles("CUSTOMER")
                .build();

        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(details);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        assertEquals(email, SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(request, response);
    }

    @Test
    void disabledUserIsNotAuthenticatedEvenWhenTokenIsValid() throws Exception {
        String token = "valid.jwt.token";
        String email = "disabled@test.com";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        UserDetails disabled = User.withUsername(email)
                .password("not-used")
                .roles("CUSTOMER")
                .disabled(true)
                .build();

        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(disabled);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void expiredOrInvalidTokenDoesNotLoadUser() throws Exception {
        String token = "expired.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractEmail(token)).thenReturn("customer@test.com");
        when(jwtService.isTokenValid(token)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
        verify(chain).doFilter(request, response);
    }
}
