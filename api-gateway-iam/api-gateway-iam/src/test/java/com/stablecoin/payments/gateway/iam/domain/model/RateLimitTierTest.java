package com.stablecoin.payments.gateway.iam.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitTierTest {

    @ParameterizedTest
    @CsvSource({
            "STARTER, 60, 10000",
            "GROWTH, 300, 100000",
            "ENTERPRISE, 1000, 1000000"
    })
    void shouldReturnCorrectLimits(RateLimitTier tier, int perMinute, int perDay) {
        assertThat(tier.requestsPerMinute()).isEqualTo(perMinute);
        assertThat(tier.requestsPerDay()).isEqualTo(perDay);
    }

    @Test
    void unlimitedShouldHaveMaxValues() {
        assertThat(RateLimitTier.UNLIMITED.requestsPerMinute()).isEqualTo(Integer.MAX_VALUE);
        assertThat(RateLimitTier.UNLIMITED.requestsPerDay()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void shouldHaveFourTiers() {
        assertThat(RateLimitTier.values()).hasSize(4);
    }
}
