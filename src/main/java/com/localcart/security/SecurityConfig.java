package com.localcart.security;

import com.localcart.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.auth.cookie.enabled:true}")
    private boolean cookieAuthenticationEnabled;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secureCookie;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomUserDetailsService userDetailsService) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider)
            throws Exception {

        http
                .csrf(csrf -> {
                    CookieCsrfTokenRepository csrfRepository =
                            CookieCsrfTokenRepository.withHttpOnlyFalse();
                    csrfRepository.setCookieCustomizer(cookie -> cookie
                            .path("/")
                            .sameSite("Lax")
                            .secure(secureCookie));
                    csrf.csrfTokenRepository(csrfRepository)
                            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                            .ignoringRequestMatchers(
                                    "/api/payments/webhook/razorpay",
                                    csrfExemptForCompatibilityMode(cookieAuthenticationEnabled));
                })

                .headers(headers -> headers
                        .addHeaderWriter(new SwaggerUiSecurityHeadersWriter()))

                .authenticationProvider(authenticationProvider)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.GET,
                                "/api/auth/csrf",
                                "/api/auth/session")
                        .permitAll()

                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/logout")
                        .permitAll()

                        .requestMatchers(
                                "/actuator/health/**",
                                "/api/test",
                                "/api/catalogue/categories",
                                "/api/payments/webhook/razorpay",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/shopkeeper/**")
                        .hasRole("SHOPKEEPER")

                        .requestMatchers("/api/delivery/**")
                        .hasRole("DELIVERY_PARTNER")

                        .requestMatchers("/api/customer/**")
                        .hasRole("CUSTOMER")

                        .anyRequest()
                        .authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Bearer compatibility is used only by isolated CI/API fixtures. A Bearer
     * header is explicitly supplied by the caller and is not an ambient browser
     * credential, so CSRF protection is unnecessary for those authenticated
     * requests. Cookie-authenticated production requests never use this
     * exemption. In compatibility mode, only login/register/logout are exempt
     * when no bearer token exists.
     */
    static RequestMatcher csrfExemptForCompatibilityMode(boolean cookieAuthenticationEnabled) {
        return request -> {
            if (cookieAuthenticationEnabled) {
                return false;
            }

            String authorization = request.getHeader("Authorization");
            if (authorization != null
                    && authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
                return true;
            }

            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                return false;
            }

            String path = request.getServletPath();
            return "/api/auth/login".equals(path)
                    || "/api/auth/register".equals(path)
                    || "/api/auth/logout".equals(path);
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
