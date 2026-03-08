package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for the FX rate lock activity.
 * <p>
 * Contains the payment and currency pair information needed by
 * S6 FX & Liquidity Engine to lock an exchange rate.
 */
public record FxLockRequest(
        String idempotencyKey,
        UUID paymentId,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal sourceAmount,
        String sourceCountry,
        String targetCountry
) {}
