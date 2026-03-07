package com.stablecoin.payments.compliance.domain.service;

import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.RiskScore;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service that calculates risk score from multiple factors.
 * Pure domain logic — no framework dependencies.
 */
@Slf4j
public class RiskScoringService {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private static final int KYC_TIER1_PENALTY = 20;
    private static final int HIGH_VALUE_PENALTY = 15;
    private static final int AML_FLAG_PENALTY = 30;
    private static final int CROSS_BORDER_PENALTY = 10;
    private static final int MAX_SCORE = 100;

    /**
     * Calculates a risk score from multiple factors on the compliance check.
     * <p>
     * Factors considered:
     * <ul>
     *   <li>KYC tier (TIER_1 adds penalty)</li>
     *   <li>Transaction amount (high-value transactions)</li>
     *   <li>AML flags</li>
     *   <li>Cross-border transactions</li>
     * </ul>
     */
    public RiskScore calculateScore(ComplianceCheck check) {
        log.info("Calculating risk score for check={}", check.checkId());

        var factors = new ArrayList<String>();
        int score = 0;

        // Factor 1: KYC tier
        if (check.kycResult() != null) {
            score += kycTierScore(check.kycResult(), factors);
        }

        // Factor 2: Transaction amount
        if (check.sourceAmount() != null
                && check.sourceAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            score += HIGH_VALUE_PENALTY;
            factors.add("high_value_transaction");
        }

        // Factor 3: AML flags
        if (check.amlResult() != null && check.amlResult().flagged()) {
            score += AML_FLAG_PENALTY;
            factors.add("aml_flagged");
        }

        // Factor 4: Cross-border
        if (check.sourceCountry() != null && check.targetCountry() != null
                && !check.sourceCountry().equals(check.targetCountry())) {
            score += CROSS_BORDER_PENALTY;
            factors.add("cross_border");
        }

        // Cap at 100
        score = Math.min(score, MAX_SCORE);
        var band = RiskScore.bandForScore(score);

        log.info("Risk score for check={}: score={}, band={}, factors={}",
                check.checkId(), score, band, factors);

        return RiskScore.builder()
                .score(score)
                .band(band)
                .factors(List.copyOf(factors))
                .build();
    }

    private int kycTierScore(KycResult kycResult, List<String> factors) {
        if (kycResult.senderKycTier() == KycTier.KYC_TIER_1) {
            factors.add("kyc_tier_1_sender");
            return KYC_TIER1_PENALTY;
        }
        return 0;
    }
}
