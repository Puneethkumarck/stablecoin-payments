package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Domain service: maps KYB provider risk signals to internal RiskTier.
 * Score > 50 = HIGH risk (disqualifies activation).
 * Score 25–50 = MEDIUM, below 25 = LOW.
 */
@Service
public class RiskTierCalculator {

    private static final int HIGH_RISK_THRESHOLD = 50;
    private static final int MEDIUM_RISK_THRESHOLD = 25;

    public RiskTier calculate(Map<String, Object> riskSignals) {
        var score = extractScore(riskSignals);
        if (score > HIGH_RISK_THRESHOLD) {
            return RiskTier.HIGH;
        }
        if (score > MEDIUM_RISK_THRESHOLD) {
            return RiskTier.MEDIUM;
        }
        return RiskTier.LOW;
    }

    private int extractScore(Map<String, Object> riskSignals) {
        if (riskSignals == null || !riskSignals.containsKey("risk_score")) {
            return 0;
        }
        var raw = riskSignals.get("risk_score");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
