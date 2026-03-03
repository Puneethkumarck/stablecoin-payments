package com.stablecoin.payments.gateway.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenTest {

    private static AccessToken.AccessTokenBuilder baseToken() {
        return AccessToken.builder()
                .jti(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .scopes(List.of("payments:read"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false);
    }

    @Nested
    @DisplayName("revoke()")
    class Revoke {

        @Test
        void shouldRevokeActiveToken() {
            var token = baseToken().build();

            token.revoke();

            assertThat(token.isRevoked()).isTrue();
            assertThat(token.getRevokedAt()).isNotNull();
        }

        @Test
        void shouldRejectRevokingAlreadyRevokedToken() {
            var token = baseToken().revoked(true).build();

            assertThatThrownBy(token::revoke)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already revoked");
        }
    }

    @Nested
    @DisplayName("isExpired()")
    class IsExpired {

        @Test
        void shouldReturnFalseForFutureExpiry() {
            var token = baseToken().expiresAt(Instant.now().plusSeconds(3600)).build();

            assertThat(token.isExpired()).isFalse();
        }

        @Test
        void shouldReturnTrueForPastExpiry() {
            var token = baseToken().expiresAt(Instant.now().minusSeconds(3600)).build();

            assertThat(token.isExpired()).isTrue();
        }
    }

    @Nested
    @DisplayName("isActive()")
    class IsActive {

        @Test
        void shouldReturnTrueForNonRevokedNonExpiredToken() {
            var token = baseToken().build();

            assertThat(token.isActive()).isTrue();
        }

        @Test
        void shouldReturnFalseForRevokedToken() {
            var token = baseToken().revoked(true).build();

            assertThat(token.isActive()).isFalse();
        }

        @Test
        void shouldReturnFalseForExpiredToken() {
            var token = baseToken().expiresAt(Instant.now().minusSeconds(1)).build();

            assertThat(token.isActive()).isFalse();
        }
    }
}
