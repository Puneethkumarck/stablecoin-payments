package com.stablecoin.payments.ledger.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Currency(
        String code,
        int decimalPrecision,
        boolean isActive,
        Instant createdAt
) {

    public Currency {
        Objects.requireNonNull(code, "code must not be null");
        if (decimalPrecision < 0 || decimalPrecision > 18) {
            throw new IllegalArgumentException("decimalPrecision must be between 0 and 18");
        }
    }
}
