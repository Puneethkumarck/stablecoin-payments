package com.stablecoin.payments.compliance.domain.model;

public record Corridor(
        String sourceCountry,
        String targetCountry,
        String sourceCurrency,
        String targetCurrency
) {

    public Corridor {
        if (sourceCountry == null || sourceCountry.isBlank()) {
            throw new IllegalArgumentException("sourceCountry is required");
        }
        if (targetCountry == null || targetCountry.isBlank()) {
            throw new IllegalArgumentException("targetCountry is required");
        }
        if (sourceCurrency == null || sourceCurrency.isBlank()) {
            throw new IllegalArgumentException("sourceCurrency is required");
        }
        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw new IllegalArgumentException("targetCurrency is required");
        }
    }

    public boolean isCrossBorder() {
        return !sourceCountry.equals(targetCountry);
    }
}
