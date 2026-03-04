package com.stablecoin.payments.merchant.iam.infrastructure.cache;

import com.stablecoin.payments.merchant.iam.domain.PermissionCacheProvider;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.Permission;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.PermissionSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPermissionCacheAdapter implements PermissionCacheProvider {

    static final Duration TTL = Duration.ofSeconds(60);
    static final String KEY_PREFIX = "perms:";
    // Sentinel stored when the user has no permissions — distinguishes "cached empty" from "cache miss"
    private static final String EMPTY_SENTINEL = "__empty__";

    private final StringRedisTemplate redis;

    @Override
    public Optional<PermissionSet> getPermissions(UUID merchantId, UUID userId) {
        var value = redis.opsForValue().get(key(merchantId, userId));
        if (value == null) {
            return Optional.empty();
        }
        if (EMPTY_SENTINEL.equals(value)) {
            return Optional.of(PermissionSet.empty());
        }
        var permissions = Arrays.stream(value.split(","))
                .map(Permission::parse)
                .collect(Collectors.toSet());
        return Optional.of(PermissionSet.of(permissions));
    }

    @Override
    public void putPermissions(UUID merchantId, UUID userId, PermissionSet permissions) {
        var value = permissions.isEmpty()
                ? EMPTY_SENTINEL
                : permissions.permissions().stream()
                        .map(p -> p.namespace() + ":" + p.action())
                        .collect(Collectors.joining(","));
        redis.opsForValue().set(key(merchantId, userId), value, TTL);
        log.debug("Cached permissions merchantId={} userId={} count={}", merchantId, userId, permissions.size());
    }

    @Override
    public void evict(UUID merchantId, UUID userId) {
        redis.delete(key(merchantId, userId));
        log.debug("Evicted permission cache merchantId={} userId={}", merchantId, userId);
    }

    @Override
    public void evictAll(UUID merchantId) {
        var pattern = KEY_PREFIX + merchantId + ":*";
        var keysToDelete = new ArrayList<String>();

        try (var cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
            cursor.forEachRemaining(keysToDelete::add);
        }

        if (!keysToDelete.isEmpty()) {
            redis.delete(keysToDelete);
            log.debug("Evicted all permission cache entries for merchantId={} count={}", merchantId, keysToDelete.size());
        }
    }

    private String key(UUID merchantId, UUID userId) {
        return KEY_PREFIX + merchantId + ":" + userId;
    }
}
