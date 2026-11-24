package com.localcart.security;

import com.localcart.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
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
                    if (cookieAuthenticationEnabled) {
                        CookieCsrfTokenRepository csrfRepository =
                                CookieCsrfTokenRepository.withHttpOnlyFalse();
                        csrfRepository.setCookieCustomizer(cookie -> cookie
                                .path("/")
                                .sameSite("Lax")
                                .secure(secureCookie));
                        csrf.csrfTokenRepository(csrfRepository)
                                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                                .ignoringRequestMatchers("/api/payments/webhook/razorpay");
                    } else {
                        csrf.disable();
                    }
                })

                .authenticationProvider(authenticationProvider)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/api/auth/**",
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

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
