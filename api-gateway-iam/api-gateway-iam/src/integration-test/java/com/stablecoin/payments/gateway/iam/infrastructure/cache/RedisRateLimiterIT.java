package com.stablecoin.payments.gateway.iam.infrastructure.cache;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitPolicy;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisRateLimiter IT")
class RedisRateLimiterIT extends AbstractIntegrationTest {

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Test
    @DisplayName("should allow request within limit")
    void shouldAllowWithinLimit() {
        var merchantId = UUID.randomUUID();
        var policy = new RateLimitPolicy(RateLimitTier.STARTER);

        var result = rateLimiter.check(merchantId, "/v1/payments", policy);

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isEqualTo(1);
        assertThat(result.limit()).isEqualTo(60);
    }

    @Test
    @DisplayName("should increment counter on each request")
    void shouldIncrementCounter() {
        var merchantId = UUID.randomUUID();
        var policy = new RateLimitPolicy(RateLimitTier.ENTERPRISE);

        rateLimiter.check(merchantId, "/v1/test", policy);
        rateLimiter.check(merchantId, "/v1/test", policy);
        var result = rateLimiter.check(merchantId, "/v1/test", policy);

        assertThat(result.currentCount()).isEqualTo(3);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("should use separate keys per endpoint")
    void shouldUseSeparateKeysPerEndpoint() {
        var merchantId = UUID.randomUUID();
        var policy = new RateLimitPolicy(RateLimitTier.STARTER);

        rateLimiter.check(merchantId, "/v1/payments", policy);
        var result = rateLimiter.check(merchantId, "/v1/api-keys", policy);

        assertThat(result.currentCount()).isEqualTo(1);
    }
}
