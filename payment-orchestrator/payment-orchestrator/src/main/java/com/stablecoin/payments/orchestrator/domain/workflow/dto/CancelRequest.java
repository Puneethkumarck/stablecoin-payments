package com.stablecoin.payments.orchestrator.domain.workflow.dto;

import java.util.UUID;

/**
 * Signal sent to the PaymentWorkflow to cancel a payment in progress.
 * <p>
 * Triggers the compensation stack in LIFO order for any completed forward steps.
 */
public record CancelRequest(
        UUID paymentId,
        String reason,
        String cancelledBy
) {}
