package com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KybStatusResult(
        UUID kybId,
        String status,
        String provider,
        String providerRef,
        Instant initiatedAt,
        Instant completedAt,
        Map<String, Object> riskSignals
) {}
