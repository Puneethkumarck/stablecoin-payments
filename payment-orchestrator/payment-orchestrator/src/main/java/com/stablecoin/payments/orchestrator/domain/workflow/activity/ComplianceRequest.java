package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for the compliance check activity.
 * <p>
 * Contains sender/recipient information and payment details
 * needed by S2 Compliance service for screening.
 */
public record ComplianceRequest(
        UUID paymentId,
        UUID senderId,
        UUID recipientId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        String targetCurrency,
        String sourceCountry,
        String targetCountry
) {}
