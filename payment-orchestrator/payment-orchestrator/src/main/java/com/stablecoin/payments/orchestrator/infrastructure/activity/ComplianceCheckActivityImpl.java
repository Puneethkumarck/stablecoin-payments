package com.stablecoin.payments.orchestrator.infrastructure.activity;

import com.stablecoin.payments.compliance.api.request.InitiateComplianceCheckRequest;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.client.ComplianceCheckClient;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceCheckActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.FAILED;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.PASSED;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.SANCTIONS_HIT;

/**
 * Temporal activity implementation that calls S2 Compliance Service via REST.
 * <p>
 * Flow: POST to initiate check, then poll GET until terminal state.
 * Heartbeats during polling to signal liveness to the Temporal server.
 * <p>
 * No compensation needed — compliance check does not move value.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceCheckActivityImpl implements ComplianceCheckActivity {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "PASSED", "FAILED", "SANCTIONS_HIT", "MANUAL_REVIEW");
    private static final long POLL_INTERVAL_MS = 2000;

    private final ComplianceCheckClient complianceCheckClient;

    @Override
    public ComplianceResult checkCompliance(ComplianceRequest request) {
        log.info("Starting compliance check for paymentId={}", request.paymentId());

        var s2Request = new InitiateComplianceCheckRequest(
                request.paymentId(),
                request.senderId(),
                request.recipientId(),
                request.sourceAmount(),
                request.sourceCurrency(),
                request.sourceCountry(),
                request.targetCountry(),
                request.targetCurrency()
        );

        var response = complianceCheckClient.initiateCheck(s2Request);
        log.info("Compliance check initiated for paymentId={}, checkId={}, status={}",
                request.paymentId(), response.checkId(), response.status());

        // Poll until terminal state
        while (!isTerminal(response.status())) {
            Activity.getExecutionContext().heartbeat(response.status());
            sleep(POLL_INTERVAL_MS);
            response = complianceCheckClient.getCheck(response.checkId());
            log.debug("Polling compliance check checkId={}, status={}",
                    response.checkId(), response.status());
        }

        return mapToResult(response);
    }

    private boolean isTerminal(String status) {
        return TERMINAL_STATUSES.contains(status);
    }

    private ComplianceResult mapToResult(ComplianceCheckResponse response) {
        return switch (response.status()) {
            case "PASSED" -> new ComplianceResult(response.checkId(), PASSED, null);
            case "SANCTIONS_HIT" -> throw ApplicationFailure.newNonRetryableFailure(
                    "Sanctions hit detected for checkId=" + response.checkId(),
                    "SANCTIONS_HIT",
                    new ComplianceResult(response.checkId(), SANCTIONS_HIT,
                            buildFailureReason(response)));
            case "FAILED", "MANUAL_REVIEW" -> new ComplianceResult(
                    response.checkId(), FAILED, buildFailureReason(response));
            default -> new ComplianceResult(response.checkId(), FAILED,
                    "Unknown compliance status: " + response.status());
        };
    }

    private String buildFailureReason(ComplianceCheckResponse response) {
        if (response.errorMessage() != null) {
            return response.errorMessage();
        }
        if ("SANCTIONS_HIT".equals(response.status()) && response.sanctionsResult() != null) {
            return "Sanctions match detected — senderHit=%s, recipientHit=%s".formatted(
                    response.sanctionsResult().senderHit(),
                    response.sanctionsResult().recipientHit());
        }
        return response.overallResult() != null
                ? response.overallResult()
                : response.status();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApplicationFailure.newFailure("Polling interrupted", "INTERRUPTED");
        }
    }
}
