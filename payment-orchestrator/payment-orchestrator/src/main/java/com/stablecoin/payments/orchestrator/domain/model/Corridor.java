package com.stablecoin.payments.orchestrator.domain.model;

/**
 * Value object representing a payment corridor (source country to target country).
 * <p>
 * Invariant: sourceCountry must not equal targetCountry.
 */
public record Corridor(String sourceCountry, String targetCountry) {

    public Corridor {
        if (sourceCountry == null || sourceCountry.isBlank()) {
            throw new IllegalArgumentException("sourceCountry is required");
        }
        if (targetCountry == null || targetCountry.isBlank()) {
            throw new IllegalArgumentException("targetCountry is required");
        }
        if (sourceCountry.equals(targetCountry)) {
            throw new IllegalArgumentException("sourceCountry must not equal targetCountry");
        }
    }
}
