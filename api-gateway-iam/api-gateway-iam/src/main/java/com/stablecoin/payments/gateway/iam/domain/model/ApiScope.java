package com.stablecoin.payments.gateway.iam.domain.model;

import java.util.Objects;

public record ApiScope(String value) {

    public ApiScope {
        Objects.requireNonNull(value, "scope value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("scope value must not be blank");
        }
        if (!value.matches("^[a-z][a-z0-9_.:-]*$")) {
            throw new IllegalArgumentException("Invalid scope format: " + value);
        }
    }
}
