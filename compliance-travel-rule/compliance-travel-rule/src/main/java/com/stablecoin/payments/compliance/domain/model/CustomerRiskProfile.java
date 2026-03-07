package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record CustomerRiskProfile(
        UUID customerId,
        KycTier kycTier,
        Instant kycVerifiedAt,
        RiskBand riskBand,
        int riskScore,
        BigDecimal perTxnLimitUsd,
        BigDecimal dailyLimitUsd,
        BigDecimal monthlyLimitUsd,
        Instant lastScoredAt,
        Instant createdAt,
        Instant updatedAt
) {}
