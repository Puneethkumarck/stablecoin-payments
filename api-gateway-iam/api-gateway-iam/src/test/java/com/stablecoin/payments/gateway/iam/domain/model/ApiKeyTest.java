package com.stablecoin.payments.gateway.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyTest {

    private static ApiKey.ApiKeyBuilder baseKey() {
        return ApiKey.builder()
                .keyId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .keyHash("abc123hash")
                .keyPrefix("pk_live_")
                .name("Production Key")
                .environment(ApiKeyEnvironment.LIVE)
                .scopes(List.of("payments:read", "payments:write"))
                .allowedIps(List.of("10.0.0.1", "10.0.0.2"))
                .active(true)
                .expiresAt(Instant.now().plusSeconds(86400))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L);
    }

    @Nested
    @DisplayName("revoke()")
    class Revoke {

        @Test
        void shouldRevokeActiveKey() {
            var key = baseKey().build();

            key.revoke();

            assertThat(key.isActive()).isFalse();
            assertThat(key.getRevokedAt()).isNotNull();
        }

        @Test
        void shouldRejectRevokingAlreadyRevokedKey() {
            var key = baseKey().active(false).build();

            assertThatThrownBy(key::revoke)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already revoked");
        }
    }

    @Nested
    @DisplayName("isExpired()")
    class IsExpired {

        @Test
        void shouldReturnFalseForFutureExpiry() {
            var key = baseKey().expiresAt(Instant.now().plusSeconds(3600)).build();

            assertThat(key.isExpired()).isFalse();
        }

        @Test
        void shouldReturnTrueForPastExpiry() {
            var key = baseKey().expiresAt(Instant.now().minusSeconds(3600)).build();

            assertThat(key.isExpired()).isTrue();
        }

        @Test
        void shouldReturnFalseWhenNoExpiry() {
            var key = baseKey().expiresAt(null).build();

            assertThat(key.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("isUsable()")
    class IsUsable {

        @Test
        void shouldReturnTrueForActiveNonExpiredKey() {
            var key = baseKey().build();

            assertThat(key.isUsable()).isTrue();
        }

        @Test
        void shouldReturnFalseForRevokedKey() {
            var key = baseKey().active(false).build();

            assertThat(key.isUsable()).isFalse();
        }

        @Test
        void shouldReturnFalseForExpiredKey() {
            var key = baseKey().expiresAt(Instant.now().minusSeconds(1)).build();

            assertThat(key.isUsable()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasScope()")
    class HasScope {

        @Test
        void shouldReturnTrueForExistingScope() {
            var key = baseKey().build();

            assertThat(key.hasScope("payments:read")).isTrue();
        }

        @Test
        void shouldReturnFalseForMissingScope() {
            var key = baseKey().build();

            assertThat(key.hasScope("admin:write")).isFalse();
        }
    }

    @Nested
    @DisplayName("isIpAllowed()")
    class IsIpAllowed {

        @Test
        void shouldReturnTrueForAllowedIp() {
            var key = baseKey().build();

            assertThat(key.isIpAllowed("10.0.0.1")).isTrue();
        }

        @Test
        void shouldReturnFalseForDeniedIp() {
            var key = baseKey().build();

            assertThat(key.isIpAllowed("192.168.1.1")).isFalse();
        }

        @Test
        void shouldAllowAnyIpWhenListIsEmpty() {
            var key = baseKey().allowedIps(List.of()).build();

            assertThat(key.isIpAllowed("192.168.1.1")).isTrue();
        }

        @Test
        void shouldAllowAnyIpWhenListIsNull() {
            var key = baseKey().allowedIps(null).build();

            assertThat(key.isIpAllowed("192.168.1.1")).isTrue();
        }
    }
}
