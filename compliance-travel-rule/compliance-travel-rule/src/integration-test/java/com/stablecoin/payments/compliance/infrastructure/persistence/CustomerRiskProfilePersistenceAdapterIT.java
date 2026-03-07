package com.stablecoin.payments.compliance.infrastructure.persistence;

import com.stablecoin.payments.compliance.AbstractIntegrationTest;
import com.stablecoin.payments.compliance.domain.model.CustomerRiskProfile;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.RiskBand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.stablecoin.payments.compliance.fixtures.CustomerRiskProfileFixtures.BASE_TIME;
import static com.stablecoin.payments.compliance.fixtures.CustomerRiskProfileFixtures.aRiskProfile;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomerRiskProfilePersistenceAdapter IT")
class CustomerRiskProfilePersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private CustomerRiskProfilePersistenceAdapter adapter;

    // ── Basic CRUD ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should save and find by customer id")
    void shouldSaveAndFindByCustomerId() {
        var profile = aRiskProfile();
        adapter.save(profile);

        assertThat(adapter.findByCustomerId(profile.customerId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .isEqualTo(profile);
    }

    @Test
    @DisplayName("should return empty when customer id not found")
    void shouldReturnEmptyWhenCustomerIdNotFound() {
        assertThat(adapter.findByCustomerId(UUID.randomUUID())).isEmpty();
    }

    // ── Update ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("should update existing profile via upsert path")
    void shouldUpdateExistingProfile() {
        var profile = aRiskProfile();
        adapter.save(profile);

        var updated = profile.toBuilder()
                .kycTier(KycTier.KYC_TIER_3)
                .riskBand(RiskBand.MEDIUM)
                .riskScore(45)
                .perTxnLimitUsd(new BigDecimal("25000.00"))
                .updatedAt(Instant.now())
                .build();
        adapter.save(updated);

        assertThat(adapter.findByCustomerId(profile.customerId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("updatedAt")
                .isEqualTo(updated);
    }

    // ── Precision & nullable ────────────────────────────────────────────

    @Test
    @DisplayName("should persist decimal limits with correct precision")
    void shouldPersistDecimalLimitsWithPrecision() {
        var profile = CustomerRiskProfile.builder()
                .customerId(UUID.randomUUID())
                .kycTier(KycTier.KYC_TIER_1)
                .riskBand(RiskBand.HIGH)
                .riskScore(65)
                .perTxnLimitUsd(new BigDecimal("12345.67"))
                .dailyLimitUsd(new BigDecimal("98765.43"))
                .monthlyLimitUsd(new BigDecimal("1234567.89"))
                .lastScoredAt(BASE_TIME)
                .createdAt(BASE_TIME)
                .updatedAt(BASE_TIME)
                .build();
        adapter.save(profile);

        assertThat(adapter.findByCustomerId(profile.customerId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .isEqualTo(profile);
    }

    @Test
    @DisplayName("should handle null kyc verified at")
    void shouldHandleNullKycVerifiedAt() {
        var profile = aRiskProfile().toBuilder()
                .kycVerifiedAt(null)
                .build();
        adapter.save(profile);

        assertThat(adapter.findByCustomerId(profile.customerId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .isEqualTo(profile);
    }
}
