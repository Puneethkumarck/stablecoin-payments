package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record SanctionsResult(
        UUID sanctionsResultId,
        UUID checkId,
        boolean senderScreened,
        boolean recipientScreened,
        boolean senderHit,
        boolean recipientHit,
        String hitDetails,
        List<String> listsChecked,
        String provider,
        String providerRef,
        Instant screenedAt
) {}
