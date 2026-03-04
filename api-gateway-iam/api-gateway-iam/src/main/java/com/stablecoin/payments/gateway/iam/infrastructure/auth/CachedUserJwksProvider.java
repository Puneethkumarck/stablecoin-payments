package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import com.stablecoin.payments.gateway.iam.domain.exception.UserJwksUnavailableException;
import com.stablecoin.payments.gateway.iam.domain.port.UserJwksProvider;
import com.stablecoin.payments.gateway.iam.infrastructure.client.MerchantIamClient;
import com.stablecoin.payments.gateway.iam.infrastructure.config.MerchantIamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class CachedUserJwksProvider implements UserJwksProvider {

    private static final String CACHE_KEY = "jwks:merchant-iam";

    private final MerchantIamClient merchantIamClient;
    private final StringRedisTemplate redis;
    private final MerchantIamProperties properties;

    @Override
    public String fetchJwks() {
        var cached = redis.opsForValue().get(CACHE_KEY);

        try {
            var jwks = merchantIamClient.fetchJwks();
            redis.opsForValue().set(CACHE_KEY, jwks, Duration.ofHours(properties.jwksCacheTtlHours()));
            log.debug("Fetched and cached JWKS from S13");
            return jwks;
        } catch (Exception e) {
            if (cached != null) {
                log.warn("S13 JWKS fetch failed, using cached value: {}", e.getMessage());
                return cached;
            }
            throw new UserJwksUnavailableException(
                    "S13 JWKS endpoint unreachable and no cached value available", e);
        }
    }
}
