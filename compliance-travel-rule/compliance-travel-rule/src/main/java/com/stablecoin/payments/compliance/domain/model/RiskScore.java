package com.stablecoin.payments.compliance.domain.model;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record RiskScore(
        int score,
        RiskBand band,
        List<String> factors
) {
    public RiskScore {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
    }

    public static RiskBand bandForScore(int score) {
        if (score <= 25) return RiskBand.LOW;
        if (score <= 50) return RiskBand.MEDIUM;
        if (score <= 75) return RiskBand.HIGH;
        return RiskBand.CRITICAL;
    }
}
