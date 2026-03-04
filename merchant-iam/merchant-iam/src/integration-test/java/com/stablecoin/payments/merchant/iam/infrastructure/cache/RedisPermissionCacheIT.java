package com.stablecoin.payments.merchant.iam.infrastructure.cache;

import com.stablecoin.payments.merchant.iam.AbstractIntegrationTest;
import com.stablecoin.payments.merchant.iam.domain.PermissionCacheProvider;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.Permission;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.PermissionSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.merchant.iam.infrastructure.cache.RedisPermissionCacheAdapter.KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

class RedisPermissionCacheIT extends AbstractIntegrationTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @Autowired
    private PermissionCacheProvider cache;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void flushRedis() {
        var keys = redis.keys("perms:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void returns_empty_on_cache_miss() {
        var result = cache.getPermissions(MERCHANT_ID, UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void stores_and_retrieves_permissions() {
        var userId = UUID.randomUUID();
        var permissions = PermissionSet.of(List.of(
                Permission.of("payments", "read"),
                Permission.of("transactions", "read")));

        cache.putPermissions(MERCHANT_ID, userId, permissions);
        var result = cache.getPermissions(MERCHANT_ID, userId);

        assertThat(result).isPresent();
        assertThat(result.get().has(Permission.of("payments", "read"))).isTrue();
        assertThat(result.get().has(Permission.of("transactions", "read"))).isTrue();
        assertThat(result.get().size()).isEqualTo(2);
    }

    @Test
    void stores_and_retrieves_empty_permission_set() {
        var userId = UUID.randomUUID();

        cache.putPermissions(MERCHANT_ID, userId, PermissionSet.empty());
        var result = cache.getPermissions(MERCHANT_ID, userId);

        assertThat(result).isPresent();
        assertThat(result.get().isEmpty()).isTrue();
    }

    @Test
    void evict_removes_entry() {
        var userId = UUID.randomUUID();
        cache.putPermissions(MERCHANT_ID, userId, PermissionSet.of(List.of(Permission.of("payments", "read"))));

        cache.evict(MERCHANT_ID, userId);

        assertThat(cache.getPermissions(MERCHANT_ID, userId)).isEmpty();
    }

    @Test
    void evict_all_removes_only_merchant_entries() {
        var merchantA = UUID.randomUUID();
        var merchantB = UUID.randomUUID();
        var userId1 = UUID.randomUUID();
        var userId2 = UUID.randomUUID();
        var userId3 = UUID.randomUUID();

        cache.putPermissions(merchantA, userId1, PermissionSet.of(List.of(Permission.of("payments", "read"))));
        cache.putPermissions(merchantA, userId2, PermissionSet.of(List.of(Permission.of("roles", "read"))));
        cache.putPermissions(merchantB, userId3, PermissionSet.of(List.of(Permission.of("team", "manage"))));

        cache.evictAll(merchantA);

        assertThat(cache.getPermissions(merchantA, userId1)).isEmpty();
        assertThat(cache.getPermissions(merchantA, userId2)).isEmpty();
        assertThat(cache.getPermissions(merchantB, userId3)).isPresent();
    }

    @Test
    void wildcard_permission_survives_round_trip() {
        var userId = UUID.randomUUID();
        var permissions = PermissionSet.of(List.of(Permission.of("*", "*")));

        cache.putPermissions(MERCHANT_ID, userId, permissions);
        var result = cache.getPermissions(MERCHANT_ID, userId);

        assertThat(result).isPresent();
        assertThat(result.get().has(Permission.of("payments", "write"))).isTrue();
        assertThat(result.get().has(Permission.of("compliance", "read"))).isTrue();
    }

    @Test
    void key_includes_merchant_id() {
        var userId = UUID.randomUUID();

        cache.putPermissions(MERCHANT_ID, userId, PermissionSet.of(List.of(Permission.of("payments", "read"))));

        assertThat(redis.hasKey(KEY_PREFIX + MERCHANT_ID + ":" + userId)).isTrue();
    }

    @Test
    void ttl_is_set_on_cached_entry() {
        var userId = UUID.randomUUID();

        cache.putPermissions(MERCHANT_ID, userId, PermissionSet.of(List.of(Permission.of("payments", "read"))));

        var ttl = redis.getExpire(KEY_PREFIX + MERCHANT_ID + ":" + userId);
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(RedisPermissionCacheAdapter.TTL.getSeconds());
    }
}
