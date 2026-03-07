package com.stablecoin.payments.compliance.application.mapper;

import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.KycResultResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.RiskScoreResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.SanctionsResultResponse;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse.TravelRuleResponse;
import com.stablecoin.payments.compliance.api.response.CustomerRiskProfileResponse;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.CustomerRiskProfile;
import org.springframework.stereotype.Component;

@Component
public class ComplianceCheckResponseMapper {

    public ComplianceCheckResponse toResponse(ComplianceCheck check) {
        return new ComplianceCheckResponse(
                check.checkId(),
                check.paymentId(),
                check.status().name(),
                check.overallResult() != null ? check.overallResult().name() : null,
                mapRiskScore(check),
                mapKycResult(check),
                mapSanctionsResult(check),
                mapTravelRule(check),
                check.errorCode(),
                check.errorMessage(),
                check.createdAt(),
                check.completedAt()
        );
    }

    public CustomerRiskProfileResponse toResponse(CustomerRiskProfile profile) {
        return new CustomerRiskProfileResponse(
                profile.customerId(),
                profile.kycTier() != null ? profile.kycTier().name() : null,
                profile.kycVerifiedAt(),
                profile.riskBand() != null ? profile.riskBand().name() : null,
                profile.riskScore(),
                profile.perTxnLimitUsd(),
                profile.dailyLimitUsd(),
                profile.monthlyLimitUsd(),
                profile.lastScoredAt()
        );
    }

    private RiskScoreResponse mapRiskScore(ComplianceCheck check) {
        if (check.riskScore() == null) {
            return null;
        }
        return new RiskScoreResponse(
                check.riskScore().score(),
                check.riskScore().band().name(),
                check.riskScore().factors()
        );
    }

    private KycResultResponse mapKycResult(ComplianceCheck check) {
        if (check.kycResult() == null) {
            return null;
        }
        return new KycResultResponse(
                check.kycResult().senderStatus().name(),
                check.kycResult().recipientStatus().name(),
                check.kycResult().senderKycTier().name()
        );
    }

    private SanctionsResultResponse mapSanctionsResult(ComplianceCheck check) {
        if (check.sanctionsResult() == null) {
            return null;
        }
        return new SanctionsResultResponse(
                check.sanctionsResult().senderHit(),
                check.sanctionsResult().recipientHit(),
                check.sanctionsResult().listsChecked()
        );
    }

    private TravelRuleResponse mapTravelRule(ComplianceCheck check) {
        if (check.travelRulePackage() == null) {
            return null;
        }
        return new TravelRuleResponse(
                check.travelRulePackage().protocol().name(),
                check.travelRulePackage().transmissionStatus().name()
        );
    }
}
