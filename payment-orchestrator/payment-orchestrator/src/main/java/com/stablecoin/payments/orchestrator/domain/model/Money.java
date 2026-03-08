package com.stablecoin.payments.orchestrator.domain.model;

import java.math.BigDecimal;

/**
 * Value object representing a monetary amount with currency.
 * <p>
 * Invariant: amount must be positive, currency must not be blank.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
    }
}
