package com.stablecoin.payments.gateway.iam.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPolicyTest {

    @Test
    void shouldDelegateLimitsToTier() {
        var policy = new RateLimitPolicy(RateLimitTier.GROWTH);

        assertThat(policy.requestsPerMinute()).isEqualTo(300);
        assertThat(policy.requestsPerDay()).isEqualTo(100_000);
    }

    @Test
    void shouldReturnTrueForUnlimited() {
        var policy = new RateLimitPolicy(RateLimitTier.UNLIMITED);

        assertThat(policy.isUnlimited()).isTrue();
    }

    @Test
    void shouldReturnFalseForNonUnlimited() {
        var policy = new RateLimitPolicy(RateLimitTier.STARTER);

        assertThat(policy.isUnlimited()).isFalse();
    }

    @Test
    void shouldRejectNullTier() {
        assertThatThrownBy(() -> new RateLimitPolicy(null))
                .isInstanceOf(NullPointerException.class);
    }
}
