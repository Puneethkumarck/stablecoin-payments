package com.stablecoin.payments.orchestrator.domain.workflow.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Signal sent to the PaymentWorkflow when fiat has been collected from the sender.
 * <p>
 * Phase 3 preparation — will be used when S3 On-Ramp is implemented.
 */
public record FiatCollectedSignal(
        UUID paymentId,
        String providerReference,
        BigDecimal collectedAmount,
        String currency
) {}
