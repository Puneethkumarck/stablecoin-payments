package com.stablecoin.payments.gateway.iam.domain.model;

import java.util.Objects;

public record Corridor(String sourceCountry, String targetCountry) {

    public Corridor {
        Objects.requireNonNull(sourceCountry, "sourceCountry must not be null");
        Objects.requireNonNull(targetCountry, "targetCountry must not be null");
        if (sourceCountry.length() != 2 || targetCountry.length() != 2) {
            throw new IllegalArgumentException("Country codes must be ISO 3166-1 alpha-2 (2 characters)");
        }
    }
}
