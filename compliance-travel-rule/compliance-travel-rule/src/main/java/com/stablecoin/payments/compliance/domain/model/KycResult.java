package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record KycResult(
        UUID kycResultId,
        UUID checkId,
        KycTier senderKycTier,
        KycStatus senderStatus,
        KycStatus recipientStatus,
        String provider,
        String providerRef,
        Instant checkedAt
) {}
