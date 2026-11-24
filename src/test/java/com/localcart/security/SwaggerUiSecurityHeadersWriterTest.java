package com.localcart.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SwaggerUiSecurityHeadersWriterTest {

    private final SwaggerUiSecurityHeadersWriter writer =
            new SwaggerUiSecurityHeadersWriter();

    @Test
    void appliesBrowserSecurityHeadersToSwaggerUi() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setServletPath("/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);

        assertEquals("default-src 'self'; base-uri 'self'; object-src 'none'; "
                        + "frame-ancestors 'none'; form-action 'self'; script-src 'self'; "
                        + "style-src 'self'; img-src 'self' data:; "
                        + "font-src 'self' data:; connect-src 'self'",
                response.getHeader("Content-Security-Policy"));
        assertEquals("require-corp",
                response.getHeader("Cross-Origin-Embedder-Policy"));
        assertEquals("same-origin",
                response.getHeader("Cross-Origin-Opener-Policy"));
        assertEquals("same-origin",
                response.getHeader("Cross-Origin-Resource-Policy"));
        assertEquals("camera=(), microphone=(), geolocation=()",
                response.getHeader("Permissions-Policy"));
    }

    @Test
    void appliesHeadersToCanonicalSwaggerUiEntryPoint() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/swagger-ui.html");
        request.setServletPath("/swagger-ui.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);

        assertEquals("same-origin",
                response.getHeader("Cross-Origin-Resource-Policy"));
    }

    @Test
    void doesNotApplySwaggerSpecificPoliciesToCustomerApiRoutes() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/customer/home");
        request.setServletPath("/api/customer/home");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);

        assertNull(response.getHeader("Content-Security-Policy"));
        assertNull(response.getHeader("Cross-Origin-Embedder-Policy"));
        assertNull(response.getHeader("Cross-Origin-Opener-Policy"));
        assertNull(response.getHeader("Cross-Origin-Resource-Policy"));
        assertNull(response.getHeader("Permissions-Policy"));
    }
}
