package com.localcart.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/**
 * Applies a restrictive browser policy to the Swagger UI only.
 *
 * The customer application may need browser features such as geolocation and
 * may load product imagery from authorised external CDNs, so these policies
 * are deliberately not imposed on every application route.
 */
public final class SwaggerUiSecurityHeadersWriter implements HeaderWriter {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "base-uri 'self'",
            "object-src 'none'",
            "frame-ancestors 'none'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self' data:",
            "connect-src 'self'"
    );

    @Override
    public void writeHeaders(
            HttpServletRequest request,
            HttpServletResponse response) {

        String path = request.getServletPath();
        if (path == null
                || !(path.equals("/swagger-ui.html") || path.startsWith("/swagger-ui/"))) {
            return;
        }

        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }
}
