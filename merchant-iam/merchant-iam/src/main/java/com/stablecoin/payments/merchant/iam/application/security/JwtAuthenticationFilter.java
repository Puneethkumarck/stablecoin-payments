package com.stablecoin.payments.merchant.iam.application.security;

import com.stablecoin.payments.merchant.iam.domain.team.JwtTokenIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenIssuer jwtTokenIssuer;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        var authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        var token = authHeader.substring(BEARER_PREFIX.length());
        try {
            var parsed = jwtTokenIssuer.parseAndVerify(token);

            var auth = new UserAuthentication(
                    parsed.userId(),
                    parsed.merchantId(),
                    parsed.roleId(),
                    parsed.role(),
                    parsed.permissions(),
                    parsed.mfaVerified());

            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"IAM-4010\",\"status\":\"Unauthorized\","
                            + "\"message\":\"Invalid or expired token\"}");
        }
    }
}
