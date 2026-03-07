package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record AmlResult(
        UUID amlResultId,
        UUID checkId,
        boolean flagged,
        List<String> flagReasons,
        String chainAnalysis,
        String provider,
        Instant screenedAt
) {}
