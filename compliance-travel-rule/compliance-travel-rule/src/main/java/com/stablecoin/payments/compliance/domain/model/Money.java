package com.stablecoin.payments.compliance.domain.model;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
    }

    public boolean isAboveThreshold(BigDecimal threshold) {
        return amount.compareTo(threshold) >= 0;
    }
}
