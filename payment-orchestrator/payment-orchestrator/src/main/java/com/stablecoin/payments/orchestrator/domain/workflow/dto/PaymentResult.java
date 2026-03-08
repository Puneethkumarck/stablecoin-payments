package com.stablecoin.payments.orchestrator.domain.workflow.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Output DTO for the PaymentWorkflow.
 * <p>
 * Contains the final result of the payment saga execution including
 * the locked FX rate, converted amount, and terminal status.
 */
public record PaymentResult(
        UUID paymentId,
        PaymentResultStatus status,
        String failureReason,
        UUID quoteId,
        BigDecimal lockedRate,
        BigDecimal targetAmount,
        String targetCurrency
) {

    public enum PaymentResultStatus {
        COMPLETED,
        FAILED
    }

    public static PaymentResult completed(UUID paymentId, UUID quoteId,
                                          BigDecimal lockedRate, BigDecimal targetAmount,
                                          String targetCurrency) {
        return new PaymentResult(paymentId, PaymentResultStatus.COMPLETED, null,
                quoteId, lockedRate, targetAmount, targetCurrency);
    }

    public static PaymentResult failed(UUID paymentId, String reason) {
        return new PaymentResult(paymentId, PaymentResultStatus.FAILED, reason,
                null, null, null, null);
    }
}
