package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.model.RateLimitEvent;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimitEventRepository;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");

    private final RateLimiter rateLimiter;
    private final MerchantRepository merchantRepository;
    private final RateLimitEventRepository rateLimitEventRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        var merchantId = extractMerchantId(auth);
        if (merchantId == null) {
            chain.doFilter(request, response);
            return;
        }
        var endpoint = request.getMethod() + " " + normalizePath(request.getRequestURI());

        var merchant = merchantRepository.findById(merchantId).orElse(null);
        if (merchant == null) {
            log.warn("Authenticated merchant not found merchantId={}", merchantId);
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"GW-1001\",\"status\":\"Unauthorized\",\"message\":\"Unknown merchant\"}");
            return;
        }

        var policy = merchant.rateLimitPolicy();
        if (policy.isUnlimited()) {
            chain.doFilter(request, response);
            return;
        }

        var result = rateLimiter.check(merchantId, endpoint, policy);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, result.limit() - result.currentCount())));

        if (!result.allowed()) {
            var retryAfter = result.retryAfterSeconds();
            response.setHeader("Retry-After", String.valueOf(retryAfter));

            persistRateLimitEvent(merchantId, endpoint, merchant.getRateLimitTier(),
                    result.currentCount(), result.limit());

            log.warn("Rate limit exceeded merchantId={} endpoint={} count={}/{}",
                    merchantId, endpoint, result.currentCount(), result.limit());

            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"GW-6001\",\"status\":\"Too Many Requests\","
                            + "\"message\":\"Rate limit exceeded. Retry after %d seconds\"}".formatted(retryAfter)
            );
            return;
        }

        chain.doFilter(request, response);
    }

    static String normalizePath(String path) {
        var segments = path.split("/");
        for (int i = 0; i < segments.length; i++) {
            if (UUID_PATTERN.matcher(segments[i]).matches()
                    || NUMERIC_PATTERN.matcher(segments[i]).matches()) {
                segments[i] = "{id}";
            }
        }
        return String.join("/", segments);
    }

    private static UUID extractMerchantId(org.springframework.security.core.Authentication auth) {
        if (auth instanceof MerchantAuthentication merchantAuth) {
            return merchantAuth.merchantId();
        }
        if (auth instanceof UserAuthentication userAuth) {
            return userAuth.merchantId();
        }
        return null;
    }

    private void persistRateLimitEvent(UUID merchantId, String endpoint, RateLimitTier tier,
                                       int requestCount, int limitValue) {
        try {
            rateLimitEventRepository.save(RateLimitEvent.builder()
                    .eventId(UUID.randomUUID())
                    .merchantId(merchantId)
                    .endpoint(endpoint)
                    .tier(tier)
                    .requestCount(requestCount)
                    .limitValue(limitValue)
                    .breached(true)
                    .occurredAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to persist rate limit event for merchantId={}: {}", merchantId, e.getMessage());
        }
    }
}
