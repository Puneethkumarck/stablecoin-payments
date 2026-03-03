package com.stablecoin.payments.gateway.iam.infrastructure.cache;

import com.stablecoin.payments.gateway.iam.domain.port.TokenRevocationCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenRevocationCache implements TokenRevocationCache {

    private static final String KEY_PREFIX = "revoked:";
    private static final String REVOKED_VALUE = "1";

    private final StringRedisTemplate redis;

    @Override
    public void markRevoked(UUID jti, Duration ttl) {
        redis.opsForValue().set(key(jti), REVOKED_VALUE, ttl);
        log.debug("Marked token revoked jti={} ttl={}", jti, ttl);
    }

    @Override
    public boolean isRevoked(UUID jti) {
        return Boolean.TRUE.equals(redis.hasKey(key(jti)));
    }

    private String key(UUID jti) {
        return KEY_PREFIX + jti;
    }
}
