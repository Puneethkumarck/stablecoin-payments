package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.port.TokenIssuer;
import com.stablecoin.payments.gateway.iam.domain.port.TokenRevocationCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.stablecoin.payments.gateway.iam.application.security.MerchantAuthentication.AuthMethod.JWT;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenIssuer tokenIssuer;
    private final TokenRevocationCache tokenRevocationCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // Skip if already authenticated (e.g. by API key filter)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        var token = authHeader.substring(BEARER_PREFIX.length());

        try {
            var parsed = tokenIssuer.parseAndVerify(token);

            if (tokenRevocationCache.isRevoked(parsed.jti())) {
                log.info("Rejected revoked JWT jti={}", parsed.jti());
                sendUnauthorized(response, "Token has been revoked");
                return;
            }

            var auth = new MerchantAuthentication(
                    parsed.merchantId(),
                    parsed.clientId(),
                    parsed.scopes(),
                    JWT
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("JWT authenticated merchantId={} clientId={}", parsed.merchantId(), parsed.clientId());
        } catch (IllegalArgumentException e) {
            log.info("JWT authentication failed: {}", e.getMessage());
            sendUnauthorized(response, "Invalid or expired token");
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"GW-1001\",\"status\":\"Unauthorized\",\"message\":\"%s\"}".formatted(message)
        );
    }
}
