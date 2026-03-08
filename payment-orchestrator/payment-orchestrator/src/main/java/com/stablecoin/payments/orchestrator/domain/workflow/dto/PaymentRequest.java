package com.stablecoin.payments.orchestrator.domain.workflow.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input DTO for the PaymentWorkflow.
 * <p>
 * Contains all the information needed to execute a cross-border payment saga.
 * Workflow ID should be set to {@code paymentId} for natural deduplication.
 */
public record PaymentRequest(
        UUID paymentId,
        String idempotencyKey,
        UUID correlationId,
        UUID senderId,
        UUID recipientId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        String targetCurrency,
        String sourceCountry,
        String targetCountry
) {}
