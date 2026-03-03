package com.stablecoin.payments.gateway.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientTest {

    private static OAuthClient.OAuthClientBuilder baseClient() {
        return OAuthClient.builder()
                .clientId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .clientSecretHash("$2a$12$hashvalue")
                .name("Test Client")
                .scopes(List.of("payments:read", "payments:write"))
                .grantTypes(List.of("client_credentials"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L);
    }

    @Nested
    @DisplayName("deactivate()")
    class Deactivate {

        @Test
        void shouldDeactivateActiveClient() {
            var client = baseClient().build();

            client.deactivate();

            assertThat(client.isActive()).isFalse();
        }

        @Test
        void shouldRejectDeactivatingAlreadyInactiveClient() {
            var client = baseClient().active(false).build();

            assertThatThrownBy(client::deactivate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already deactivated");
        }
    }

    @Nested
    @DisplayName("hasScope()")
    class HasScope {

        @Test
        void shouldReturnTrueForExistingScope() {
            var client = baseClient().build();

            assertThat(client.hasScope("payments:read")).isTrue();
        }

        @Test
        void shouldReturnFalseForMissingScope() {
            var client = baseClient().build();

            assertThat(client.hasScope("admin:write")).isFalse();
        }

        @Test
        void shouldRejectNullScope() {
            var client = baseClient().build();

            assertThatThrownBy(() -> client.hasScope(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
