package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result DTO from the FX rate lock activity.
 * <p>
 * Contains the locked quote details including rate, converted amount,
 * and quote expiration for the payment saga.
 */
public record FxLockResult(
        UUID quoteId,
        BigDecimal lockedRate,
        BigDecimal targetAmount,
        String targetCurrency,
        FxLockStatus status,
        String failureReason
) {

    public enum FxLockStatus {
        LOCKED,
        FAILED,
        INSUFFICIENT_LIQUIDITY
    }

    // Note: convenience methods like isLocked() are intentionally omitted
    // to avoid Jackson serialization issues with Temporal SDK's internal serializer.
    // Use status() == FxLockStatus.LOCKED directly in workflow code.
}
