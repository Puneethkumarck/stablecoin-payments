package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyExpiredException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyRevokedException;
import com.stablecoin.payments.gateway.iam.domain.exception.IpNotAllowedException;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.stablecoin.payments.gateway.iam.application.security.MerchantAuthentication.AuthMethod.API_KEY;

@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // Skip if already authenticated (e.g. by JWT filter)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            var sourceIp = request.getRemoteAddr();
            var apiKey = apiKeyService.validate(apiKeyHeader, sourceIp);

            var auth = new MerchantAuthentication(
                    apiKey.getMerchantId(),
                    apiKey.getKeyId(),
                    apiKey.getScopes(),
                    API_KEY
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("API key authenticated merchantId={} keyPrefix={}",
                    apiKey.getMerchantId(), apiKey.getKeyPrefix());
        } catch (ApiKeyNotFoundException | ApiKeyRevokedException
                 | ApiKeyExpiredException | IpNotAllowedException e) {
            log.info("API key authentication failed: {}", e.getMessage());
            sendUnauthorized(response);
            return;
        } catch (RuntimeException e) {
            log.error("Unexpected error during API key authentication", e);
            throw e;
        }

        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"GW-1001\",\"status\":\"Unauthorized\",\"message\":\"Invalid API key\"}");
    }
}
