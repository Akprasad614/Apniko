package com.localcart.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigCsrfMatcherTest {

    @Test
    void bearerCompatibilityExemptsRequestsWithAnExplicitBearerHeader() {
        MockHttpServletRequest request = request(
                "POST", "/api/customer/cart/12/checkout");
        request.addHeader("Authorization", "Bearer ci-only-token");

        assertTrue(SecurityConfig.csrfExemptForCompatibilityMode(false).matches(request));
    }

    @Test
    void cookieAuthenticationNeverUsesTheBearerCompatibilityExemption() {
        MockHttpServletRequest request = request(
                "POST", "/api/customer/cart/12/checkout");
        request.addHeader("Authorization", "Bearer unexpected-token");

        assertFalse(SecurityConfig.csrfExemptForCompatibilityMode(true).matches(request));
    }

    @Test
    void bearerCompatibilityExemptsOnlyTheUnauthenticatedAuthHandshakePosts() {
        var matcher = SecurityConfig.csrfExemptForCompatibilityMode(false);

        assertTrue(matcher.matches(request("POST", "/api/auth/login")));
        assertTrue(matcher.matches(request("POST", "/api/auth/register")));
        assertTrue(matcher.matches(request("POST", "/api/auth/logout")));

        assertFalse(matcher.matches(request("POST", "/api/admin/catalogue/import")));
        assertFalse(matcher.matches(request("POST", "/api/customer/cart/12/checkout")));
    }

    @Test
    void nonBearerAuthorizationHeadersDoNotBypassCsrf() {
        MockHttpServletRequest request = request(
                "POST", "/api/customer/cart/12/checkout");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        assertFalse(SecurityConfig.csrfExemptForCompatibilityMode(false).matches(request));
    }

    @Test
    void safeRequestsDoNotNeedAnExemption() {
        MockHttpServletRequest request = request("GET", "/api/customer/orders");

        assertFalse(SecurityConfig.csrfExemptForCompatibilityMode(false).matches(request));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
