package com.stablecoin.payments.compliance.domain.service;

import com.stablecoin.payments.compliance.domain.model.CustomerRiskProfile;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.RiskScore;
import com.stablecoin.payments.compliance.domain.model.RiskScoringContext;
import com.stablecoin.payments.compliance.domain.model.RiskScoringWeights;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service that calculates a 0-100 risk score from multiple weighted factors.
 * Factors: KYC tier, high-value amount, AML flags, cross-border, corridor risk,
 * new customer, amount-to-limit ratio, transaction velocity.
 */
@Slf4j
@Service
public class RiskScoringService {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private static final int VELOCITY_THRESHOLD = 10;
    private static final BigDecimal AMOUNT_TO_LIMIT_RATIO_THRESHOLD = new BigDecimal("0.80");
    private static final int MAX_SCORE = 100;

    private final RiskScoringWeights weights;

    public RiskScoringService(RiskScoringWeights weights) {
        this.weights = weights;
    }

    /**
     * Calculates a risk score from multiple factors using the scoring context.
     * <p>
     * Factors considered (each contributes configurable penalty points):
     * <ul>
     *   <li>KYC tier (TIER_1 adds penalty — lowest verification level)</li>
     *   <li>Transaction amount (high-value threshold: >= $10,000)</li>
     *   <li>AML flags from screening</li>
     *   <li>Cross-border transaction</li>
     *   <li>Corridor risk (configurable per country pair)</li>
     *   <li>New customer (no existing risk profile)</li>
     *   <li>Amount relative to tier limits (>= 80% of per-txn limit)</li>
     *   <li>Transaction velocity (>= 10 recent transactions)</li>
     * </ul>
     */
    public RiskScore calculateScore(RiskScoringContext context) {
        var check = context.check();
        log.info("Calculating risk score for check={}", check.checkId());

        var factors = new ArrayList<String>();
        int score = 0;

        // Factor 1: KYC tier
        if (check.kycResult() != null
                && check.kycResult().senderKycTier() == KycTier.KYC_TIER_1) {
            score += weights.kycTier1Penalty();
            factors.add("kyc_tier_1_sender");
        }

        // Factor 2: High-value transaction
        if (check.sourceAmount() != null
                && check.sourceAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            score += weights.highValuePenalty();
            factors.add("high_value_transaction");
        }

        // Factor 3: AML flags
        if (check.amlResult() != null && check.amlResult().flagged()) {
            score += weights.amlFlagPenalty();
            factors.add("aml_flagged");
        }

        // Factor 4: Cross-border
        if (check.sourceCountry() != null && check.targetCountry() != null
                && !check.sourceCountry().equals(check.targetCountry())) {
            score += weights.crossBorderPenalty();
            factors.add("cross_border");
        }

        // Factor 5: Corridor risk (configurable per country pair)
        if (check.sourceCountry() != null && check.targetCountry() != null) {
            int corridorRisk = weights.corridorRisk(check.sourceCountry(), check.targetCountry());
            if (corridorRisk > 0) {
                score += corridorRisk;
                factors.add("high_risk_corridor");
            }
        }

        // Factor 6: New customer (no existing risk profile)
        if (context.customerProfile() == null) {
            score += weights.newCustomerPenalty();
            factors.add("new_customer");
        }

        // Factor 7: Amount relative to tier limit
        score += amountToLimitScore(check.sourceAmount(), context.customerProfile(), factors);

        // Factor 8: Transaction velocity
        if (context.recentTransactionCount() >= VELOCITY_THRESHOLD) {
            score += weights.highVelocityPenalty();
            factors.add("high_velocity");
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

    private int amountToLimitScore(BigDecimal sourceAmount, CustomerRiskProfile profile,
                                   List<String> factors) {
        if (sourceAmount == null || profile == null || profile.perTxnLimitUsd() == null) {
            return 0;
        }
        if (profile.perTxnLimitUsd().compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        var ratio = sourceAmount.divide(profile.perTxnLimitUsd(), 4, java.math.RoundingMode.HALF_UP);
        if (ratio.compareTo(AMOUNT_TO_LIMIT_RATIO_THRESHOLD) >= 0) {
            factors.add("amount_near_limit");
            return weights.amountToLimitRatioPenalty();
        }
        return 0;
    }
}
