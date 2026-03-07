package com.stablecoin.payments.compliance.domain.service;

import com.stablecoin.payments.compliance.domain.model.AmlResult;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.model.RiskBand;
import com.stablecoin.payments.compliance.domain.model.RiskScore;
import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import com.stablecoin.payments.compliance.domain.model.TravelRulePackage;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain service that orchestrates the compliance check pipeline.
 * Pure domain logic — no framework dependencies.
 */
@Slf4j
public class ComplianceCheckService {

    private static final BigDecimal TRAVEL_RULE_THRESHOLD_USD = new BigDecimal("1000");
    private static final BigDecimal TRAVEL_RULE_THRESHOLD_EUR = new BigDecimal("1000");

    public ComplianceCheck initiate(UUID paymentId, UUID senderId, UUID recipientId,
                                    Money sourceAmount, String sourceCountry,
                                    String targetCountry, String targetCurrency) {
        log.info("Initiating compliance check for payment={}", paymentId);
        return ComplianceCheck.initiate(paymentId, senderId, recipientId,
                sourceAmount, sourceCountry, targetCountry, targetCurrency);
    }

    public ComplianceCheck recordKycResult(ComplianceCheck check, KycResult kycResult) {
        log.info("Recording KYC result for check={}, senderStatus={}, recipientStatus={}",
                check.checkId(), kycResult.senderStatus(), kycResult.recipientStatus());

        var inProgress = check.startKyc();

        if (kycResult.senderStatus() == KycStatus.REJECTED
                || kycResult.recipientStatus() == KycStatus.REJECTED) {
            log.warn("KYC failed for check={}", check.checkId());
            return inProgress.failKyc(kycResult);
        }

        return inProgress.passKyc(kycResult);
    }

    public ComplianceCheck recordSanctionsResult(ComplianceCheck check, SanctionsResult sanctionsResult) {
        log.info("Recording sanctions result for check={}, senderHit={}, recipientHit={}",
                check.checkId(), sanctionsResult.senderHit(), sanctionsResult.recipientHit());

        if (sanctionsResult.senderHit() || sanctionsResult.recipientHit()) {
            log.warn("Sanctions hit detected for check={}", check.checkId());
            return check.sanctionsHitDetected(sanctionsResult);
        }

        return check.sanctionsClear(sanctionsResult);
    }

    public ComplianceCheck recordAmlResult(ComplianceCheck check, AmlResult amlResult) {
        log.info("Recording AML result for check={}, flagged={}", check.checkId(), amlResult.flagged());

        if (amlResult.flagged()) {
            log.warn("AML flagged for check={}, reasons={}", check.checkId(), amlResult.flagReasons());
            return check.amlFlagged(amlResult);
        }

        return check.amlClear(amlResult);
    }

    /**
     * Records the risk score on the compliance check.
     * CRITICAL band blocks the payment and routes to MANUAL_REVIEW.
     * All other bands proceed to TRAVEL_RULE_PACKAGING.
     */
    public ComplianceCheck recordRiskScore(ComplianceCheck check, RiskScore riskScore) {
        log.info("Recording risk score for check={}, score={}, band={}",
                check.checkId(), riskScore.score(), riskScore.band());

        if (riskScore.band() == RiskBand.CRITICAL) {
            log.warn("Critical risk score for check={}, score={} — blocking payment",
                    check.checkId(), riskScore.score());
            return check.riskCritical(riskScore);
        }

        return check.riskScored(riskScore);
    }

    public ComplianceCheck recordTravelRuleResult(ComplianceCheck check, TravelRulePackage travelRulePackage) {
        log.info("Recording travel rule result for check={}", check.checkId());
        return check.completeTravelRule(travelRulePackage);
    }

    public ComplianceCheck skipTravelRule(ComplianceCheck check) {
        log.info("Skipping travel rule for check={} (below threshold)", check.checkId());
        return check.completeTravelRule(null);
    }

    /**
     * Determines whether a compliance check requires Travel Rule data packaging
     * based on the FATF threshold (>= $1,000 / EUR 1,000).
     */
    public boolean requiresTravelRule(ComplianceCheck check) {
        var amount = check.sourceAmount();
        var currency = check.sourceCurrency();

        if (amount == null || currency == null) {
            return true;
        }

        if ("USD".equals(currency)) {
            return amount.compareTo(TRAVEL_RULE_THRESHOLD_USD) >= 0;
        }
        if ("EUR".equals(currency)) {
            return amount.compareTo(TRAVEL_RULE_THRESHOLD_EUR) >= 0;
        }

        // Default: require travel rule for unknown currencies
        return true;
    }
}
