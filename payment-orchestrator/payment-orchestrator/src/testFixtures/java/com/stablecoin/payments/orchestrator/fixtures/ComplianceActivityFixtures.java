package com.stablecoin.payments.orchestrator.fixtures;

import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SENDER_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.CHECK_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.PAYMENT_ID;

/**
 * Test fixture factory methods for compliance activity DTOs.
 */
public final class ComplianceActivityFixtures {

    private ComplianceActivityFixtures() {}

    public static ComplianceRequest aComplianceRequest() {
        return new ComplianceRequest(
                PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                new BigDecimal("1000.00"), "USD", "EUR", "US", "DE");
    }

    public static ComplianceCheckResponse aComplianceResponse(String status, String overallResult) {
        return new ComplianceCheckResponse(
                CHECK_ID, PAYMENT_ID, status, overallResult,
                new ComplianceCheckResponse.RiskScoreResponse(25, "LOW", List.of()),
                new ComplianceCheckResponse.KycResultResponse("VERIFIED", "VERIFIED", "ENHANCED"),
                new ComplianceCheckResponse.SanctionsResultResponse(false, false, List.of("OFAC", "EU")),
                new ComplianceCheckResponse.TravelRuleResponse("IVMS101", "PENDING"),
                null, null, Instant.now(), Instant.now());
    }

    public static ComplianceCheckResponse aSanctionsHitResponse() {
        return new ComplianceCheckResponse(
                CHECK_ID, PAYMENT_ID, "SANCTIONS_HIT", "SANCTIONS_HIT",
                null, null,
                new ComplianceCheckResponse.SanctionsResultResponse(true, false, List.of("OFAC")),
                null, null, "Sanctions match detected", Instant.now(), Instant.now());
    }
}
