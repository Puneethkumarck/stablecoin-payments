package com.stablecoin.payments.gateway.iam.infrastructure.cache;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisApiKeyCache IT")
class RedisApiKeyCacheIT extends AbstractIntegrationTest {

    @Autowired
    private RedisApiKeyCache cache;

    @Test
    @DisplayName("should return empty for cache miss")
    void shouldReturnEmptyForMiss() {
        var result = cache.get("nonexistent_hash");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should put and get key id")
    void shouldPutAndGet() {
        var hash = "sha256_" + UUID.randomUUID().toString().replace("-", "");
        var keyId = UUID.randomUUID().toString();

        cache.put(hash, keyId);

        var result = cache.get(hash);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(keyId);
    }

    @Test
    @DisplayName("should mark invalid and detect sentinel")
    void shouldMarkInvalid() {
        var hash = "sha256_invalid_key";
        cache.markInvalid(hash);

        var result = cache.get(hash);
        assertThat(result).isPresent();
        assertThat(cache.isInvalidSentinel(result.get())).isTrue();
    }

    @Test
    @DisplayName("should evict cached key")
    void shouldEvict() {
        var hash = "sha256_to_evict";
        cache.put(hash, "some-id");
        assertThat(cache.get(hash)).isPresent();

        cache.evict(hash);
        assertThat(cache.get(hash)).isEmpty();
    }
}
