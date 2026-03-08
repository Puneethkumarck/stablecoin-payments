package com.stablecoin.payments.compliance.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerRiskProfileResponse(
        UUID customerId,
        String kycTier,
        Instant kycVerifiedAt,
        String riskBand,
        int riskScore,
        BigDecimal perTxnLimitUsd,
        BigDecimal dailyLimitUsd,
        BigDecimal monthlyLimitUsd,
        Instant lastScoredAt
) {}
