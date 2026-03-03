package com.stablecoin.payments.gateway.iam.infrastructure.cache;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisTokenRevocationCache IT")
class RedisTokenRevocationCacheIT extends AbstractIntegrationTest {

    @Autowired
    private RedisTokenRevocationCache cache;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("should mark token as revoked and check")
    void shouldMarkAndCheck() {
        var jti = UUID.randomUUID();
        assertThat(cache.isRevoked(jti)).isFalse();

        cache.markRevoked(jti, Duration.ofMinutes(5));

        assertThat(cache.isRevoked(jti)).isTrue();
    }

    @Test
    @DisplayName("should store key with TTL")
    void shouldStoreWithTtl() {
        var jti = UUID.randomUUID();
        cache.markRevoked(jti, Duration.ofSeconds(30));

        var ttl = redis.getExpire("revoked:" + jti);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("should return false for non-existent token")
    void shouldReturnFalseForNonExistent() {
        assertThat(cache.isRevoked(UUID.randomUUID())).isFalse();
    }
}
