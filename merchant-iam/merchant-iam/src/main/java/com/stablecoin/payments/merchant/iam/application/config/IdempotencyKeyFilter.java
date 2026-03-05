package com.stablecoin.payments.merchant.iam.application.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Enforces presence of {@code Idempotency-Key} header on state-mutating endpoints
 * (POST, PATCH, DELETE) — excluding auth and invitation endpoints which use
 * tokens as implicit idempotency controls.
 */
@Slf4j
@Component
@Order(2)
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PATCH", "DELETE");
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/v1/auth/",
            "/v1/invitations/",
            "/v1/.well-known/",
            "/actuator/"
    );

    /** Path segments that mark a sub-resource as auth-related (login, refresh, logout, mfa). */
    private static final Set<String> AUTH_PATH_SEGMENTS = Set.of(
            "/auth/login", "/auth/refresh", "/auth/logout", "/auth/mfa"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (requiresIdempotencyKey(request)) {
            var key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
            if (key == null || key.isBlank()) {
                log.info("Missing Idempotency-Key header for {} {}", request.getMethod(), request.getRequestURI());
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"code\":\"IAM-0001\",\"status\":\"Bad Request\"," +
                        "\"message\":\"Idempotency-Key header is required for mutating requests\",\"errors\":{}}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean requiresIdempotencyKey(HttpServletRequest request) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return false;
        }
        var uri = request.getRequestURI();
        if (EXEMPT_PREFIXES.stream().anyMatch(uri::startsWith)) {
            return false;
        }
        // Exempt merchant-scoped auth endpoints: /v1/merchants/{id}/auth/login etc.
        return AUTH_PATH_SEGMENTS.stream().noneMatch(uri::contains);
    }
}
