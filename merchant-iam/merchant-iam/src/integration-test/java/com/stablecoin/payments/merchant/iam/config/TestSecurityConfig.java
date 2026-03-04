package com.stablecoin.payments.merchant.iam.config;

import com.stablecoin.payments.merchant.iam.application.security.UserAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@TestConfiguration
public class TestSecurityConfig {

    /** Fixed test user ID — used as callerId in controllers. */
    static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new TestUserAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Injects a synthetic {@link UserAuthentication} into the SecurityContext for every request.
     * Extracts merchantId from the URL path if present (e.g. /v1/merchants/{merchantId}/...).
     */
    static class TestUserAuthenticationFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                var merchantId = extractMerchantId(request.getRequestURI());
                var auth = new UserAuthentication(
                        TEST_USER_ID,
                        merchantId,
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        "ADMIN",
                        List.of("*:*"),
                        false
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            filterChain.doFilter(request, response);
        }

        private UUID extractMerchantId(String uri) {
            // Pattern: /v1/merchants/{merchantId}/...
            var parts = uri.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("merchants".equals(parts[i])) {
                    try {
                        return UUID.fromString(parts[i + 1]);
                    } catch (IllegalArgumentException e) {
                        break;
                    }
                }
            }
            return UUID.fromString("00000000-0000-0000-0000-000000000099");
        }
    }
}
