package com.stablecoin.payments.compliance.fixtures;

import com.stablecoin.payments.compliance.domain.model.CustomerRiskProfile;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.RiskBand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CustomerRiskProfileFixtures {

    public static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private CustomerRiskProfileFixtures() {}

    public static CustomerRiskProfile aRiskProfile() {
        return CustomerRiskProfile.builder()
                .customerId(UUID.randomUUID())
                .kycTier(KycTier.KYC_TIER_2)
                .kycVerifiedAt(BASE_TIME)
                .riskBand(RiskBand.LOW)
                .riskScore(20)
                .perTxnLimitUsd(new BigDecimal("10000.00"))
                .dailyLimitUsd(new BigDecimal("50000.00"))
                .monthlyLimitUsd(new BigDecimal("500000.00"))
                .lastScoredAt(BASE_TIME)
                .createdAt(BASE_TIME)
                .updatedAt(BASE_TIME)
                .build();
    }
}
