package com.stablecoin.payments.compliance.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplianceCheckResponse(
        UUID checkId,
        UUID paymentId,
        String status,
        String overallResult,
        RiskScoreResponse riskScore,
        KycResultResponse kycResult,
        SanctionsResultResponse sanctionsResult,
        TravelRuleResponse travelRule,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant completedAt
) {

    public record RiskScoreResponse(int score, String band, List<String> factors) {}

    public record KycResultResponse(String senderStatus, String recipientStatus, String senderKycTier) {}

    public record SanctionsResultResponse(boolean senderHit, boolean recipientHit, List<String> listsChecked) {}

    public record TravelRuleResponse(String protocol, String transmissionStatus) {}
}
