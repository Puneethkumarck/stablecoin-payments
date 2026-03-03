package com.stablecoin.payments.gateway.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantTest {

    private static Merchant.MerchantBuilder baseMerchant() {
        return Merchant.builder()
                .merchantId(UUID.randomUUID())
                .externalId(UUID.randomUUID())
                .name("Test Corp")
                .country("US")
                .scopes(List.of("payments:read", "payments:write"))
                .corridors(List.of(new Corridor("US", "DE")))
                .status(MerchantStatus.PENDING)
                .kybStatus(KybStatus.PENDING)
                .rateLimitTier(RateLimitTier.STARTER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L);
    }

    @Nested
    @DisplayName("activate()")
    class Activate {

        @Test
        void shouldActivatePendingMerchant() {
            var merchant = baseMerchant().build();

            merchant.activate();

            assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
            assertThat(merchant.getKybStatus()).isEqualTo(KybStatus.VERIFIED);
        }

        @Test
        void shouldRejectActivationOfActiveMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.ACTIVE).build();

            assertThatThrownBy(merchant::activate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only PENDING");
        }

        @Test
        void shouldRejectActivationOfSuspendedMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.SUSPENDED).build();

            assertThatThrownBy(merchant::activate)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldRejectActivationOfClosedMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.CLOSED).build();

            assertThatThrownBy(merchant::activate)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("suspend()")
    class Suspend {

        @Test
        void shouldSuspendActiveMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.ACTIVE).build();

            merchant.suspend();

            assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
        }

        @Test
        void shouldRejectSuspensionOfPendingMerchant() {
            var merchant = baseMerchant().build();

            assertThatThrownBy(merchant::suspend)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only ACTIVE");
        }

        @Test
        void shouldRejectSuspensionOfClosedMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.CLOSED).build();

            assertThatThrownBy(merchant::suspend)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("close()")
    class Close {

        @Test
        void shouldClosePendingMerchant() {
            var merchant = baseMerchant().build();

            merchant.close();

            assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.CLOSED);
        }

        @Test
        void shouldCloseActiveMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.ACTIVE).build();

            merchant.close();

            assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.CLOSED);
        }

        @Test
        void shouldCloseSuspendedMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.SUSPENDED).build();

            merchant.close();

            assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.CLOSED);
        }

        @Test
        void shouldRejectClosingAlreadyClosedMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.CLOSED).build();

            assertThatThrownBy(merchant::close)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already closed");
        }
    }

    @Nested
    @DisplayName("hasScope()")
    class HasScope {

        @Test
        void shouldReturnTrueForExistingScope() {
            var merchant = baseMerchant().build();

            assertThat(merchant.hasScope("payments:read")).isTrue();
        }

        @Test
        void shouldReturnFalseForMissingScope() {
            var merchant = baseMerchant().build();

            assertThat(merchant.hasScope("admin:write")).isFalse();
        }

        @Test
        void shouldRejectNullScope() {
            var merchant = baseMerchant().build();

            assertThatThrownBy(() -> merchant.hasScope(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isActive()")
    class IsActive {

        @Test
        void shouldReturnTrueForActiveMerchant() {
            var merchant = baseMerchant().status(MerchantStatus.ACTIVE).build();

            assertThat(merchant.isActive()).isTrue();
        }

        @Test
        void shouldReturnFalseForPendingMerchant() {
            var merchant = baseMerchant().build();

            assertThat(merchant.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("rateLimitPolicy()")
    class RateLimitPolicyTest {

        @Test
        void shouldReturnPolicyFromTier() {
            var merchant = baseMerchant().rateLimitTier(RateLimitTier.ENTERPRISE).build();

            var policy = merchant.rateLimitPolicy();

            assertThat(policy.requestsPerMinute()).isEqualTo(1_000);
            assertThat(policy.requestsPerDay()).isEqualTo(1_000_000);
        }

        @Test
        void shouldDefaultToStarterWhenTierIsNull() {
            var merchant = baseMerchant().rateLimitTier(null).build();

            var policy = merchant.rateLimitPolicy();

            assertThat(policy.requestsPerMinute()).isEqualTo(60);
        }
    }
}
