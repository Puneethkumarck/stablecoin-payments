package com.stablecoin.payments.gateway.iam.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisApiKeyCache {

    private static final String KEY_PREFIX = "apikey:";
    static final Duration TTL = Duration.ofSeconds(60);
    private static final String INVALID_SENTINEL = "__invalid__";

    private final StringRedisTemplate redis;

    public Optional<String> get(String keyHash) {
        var value = redis.opsForValue().get(key(keyHash));
        if (value == null) {
            return Optional.empty();
        }
        if (INVALID_SENTINEL.equals(value)) {
            return Optional.of(INVALID_SENTINEL);
        }
        return Optional.of(value);
    }

    public void put(String keyHash, String keyId) {
        redis.opsForValue().set(key(keyHash), keyId, TTL);
        log.debug("Cached API key hash={}", keyHash.substring(0, 8));
    }

    public void markInvalid(String keyHash) {
        redis.opsForValue().set(key(keyHash), INVALID_SENTINEL, TTL);
    }

    public void evict(String keyHash) {
        redis.delete(key(keyHash));
    }

    public boolean isInvalidSentinel(String value) {
        return INVALID_SENTINEL.equals(value);
    }

    private String key(String keyHash) {
        return KEY_PREFIX + keyHash;
    }
}
