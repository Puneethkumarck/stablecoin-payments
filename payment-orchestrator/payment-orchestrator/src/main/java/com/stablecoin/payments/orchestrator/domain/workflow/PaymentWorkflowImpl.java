package com.stablecoin.payments.orchestrator.domain.workflow;

import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceCheckActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.CancelRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.ChainConfirmedSignal;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.FiatCollectedSignal;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Deterministic Temporal workflow implementation for the cross-border payment saga.
 * <p>
 * <strong>Determinism rules enforced:</strong>
 * <ul>
 *   <li>Uses {@link Workflow#currentTimeMillis()} — never {@code System.currentTimeMillis()}</li>
 *   <li>Uses {@link Workflow#getLogger(Class)} — never SLF4J directly</li>
 *   <li>No {@code Random}, no I/O, no {@code Thread.sleep}</li>
 * </ul>
 * <p>
 * <strong>Saga compensation:</strong> compensation actions are pushed onto a LIFO stack
 * after each forward step succeeds. On cancel signal, the stack is unwound in reverse order.
 * <p>
 * <strong>Phase 2 scope:</strong> compliance check + FX lock only. Fiat collection,
 * on-chain transfer, and off-ramp are Phase 3 stubs.
 */
public class PaymentWorkflowImpl implements PaymentWorkflow {

    private Logger log;

    private final ComplianceCheckActivity complianceActivity = Workflow.newActivityStub(
            ComplianceCheckActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setDoNotRetry(
                                    "SANCTIONS_HIT",
                                    IllegalArgumentException.class.getName()
                            )
                            .build())
                    .build());

    private final FxLockActivity fxLockActivity = Workflow.newActivityStub(
            FxLockActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setDoNotRetry(
                                    "INSUFFICIENT_LIQUIDITY",
                                    IllegalArgumentException.class.getName()
                            )
                            .build())
                    .build());

    // Workflow state — deterministic, no external I/O
    private String currentState = "INITIATED";
    private boolean cancelRequested;
    private CancelRequest cancelReason;
    private final Deque<String> compensationStack = new ArrayDeque<>();

    // Phase 3 signal state
    @SuppressWarnings("unused")
    private FiatCollectedSignal fiatCollectedSignal;
    @SuppressWarnings("unused")
    private ChainConfirmedSignal chainConfirmedSignal;

    @Override
    public PaymentResult executePayment(PaymentRequest request) {
        log = Workflow.getLogger(PaymentWorkflowImpl.class);
        log.info("Starting payment workflow for paymentId={}", request.paymentId());

        // ── Step 1: Compliance Check ────────────────────────────────────
        currentState = "COMPLIANCE_CHECK";
        log.info("Step 1: Running compliance check for paymentId={}", request.paymentId());

        ComplianceResult complianceResult;
        try {
            complianceResult = complianceActivity.checkCompliance(new ComplianceRequest(
                    request.paymentId(),
                    request.senderId(),
                    request.recipientId(),
                    request.sourceAmount(),
                    request.sourceCurrency(),
                    request.targetCurrency(),
                    request.sourceCountry(),
                    request.targetCountry()
            ));
        } catch (Exception e) {
            currentState = "FAILED";
            log.error("Compliance check failed with exception for paymentId={}",
                    request.paymentId(), e);
            return PaymentResult.failed(request.paymentId(),
                    "Compliance check failed: " + e.getMessage());
        }

        if (complianceResult.status() != ComplianceResult.ComplianceStatus.PASSED) {
            currentState = "FAILED";
            log.info("Compliance check rejected for paymentId={}: {}",
                    request.paymentId(), complianceResult.failureReason());
            return PaymentResult.failed(request.paymentId(),
                    "Compliance check failed: " + complianceResult.failureReason());
        }

        // No compensation needed for compliance — it's a read-only check
        log.info("Compliance check passed for paymentId={}", request.paymentId());

        // Check for cancellation between steps
        if (cancelRequested) {
            return handleCancellation(request);
        }

        // ── Step 2: FX Rate Lock ────────────────────────────────────────
        currentState = "FX_LOCKING";
        log.info("Step 2: Locking FX rate for paymentId={}", request.paymentId());

        FxLockResult fxResult;
        try {
            fxResult = fxLockActivity.lockFxRate(new FxLockRequest(
                    request.paymentId(),
                    request.sourceCurrency(),
                    request.targetCurrency(),
                    request.sourceAmount(),
                    request.sourceCountry(),
                    request.targetCountry()
            ));
        } catch (Exception e) {
            currentState = "FAILED";
            log.error("FX lock failed with exception for paymentId={}",
                    request.paymentId(), e);
            return PaymentResult.failed(request.paymentId(),
                    "FX rate lock failed: " + e.getMessage());
        }

        if (fxResult.status() != FxLockResult.FxLockStatus.LOCKED) {
            currentState = "FAILED";
            log.info("FX lock rejected for paymentId={}: {}",
                    request.paymentId(), fxResult.failureReason());
            return PaymentResult.failed(request.paymentId(),
                    "FX rate lock failed: " + fxResult.failureReason());
        }

        // FX lock succeeded — push compensation (release lock) onto stack
        compensationStack.push("RELEASE_FX_LOCK:" + fxResult.quoteId());
        currentState = "FX_LOCKED";
        log.info("FX rate locked for paymentId={}, quoteId={}, rate={}",
                request.paymentId(), fxResult.quoteId(), fxResult.lockedRate());

        // Check for cancellation between steps
        if (cancelRequested) {
            return handleCancellation(request);
        }

        // ── Phase 3 stubs: Fiat Collection, On-Chain, Off-Ramp ──────────
        // These steps will be implemented in Phase 3 (STA-110+).
        // For Phase 2, the workflow completes after FX lock.
        currentState = "COMPLETED";
        log.info("Payment workflow completed for paymentId={}", request.paymentId());

        return PaymentResult.completed(
                request.paymentId(),
                fxResult.quoteId(),
                fxResult.lockedRate(),
                fxResult.targetAmount(),
                fxResult.targetCurrency()
        );
    }

    @Override
    public void onFiatCollected(FiatCollectedSignal signal) {
        this.fiatCollectedSignal = signal;
    }

    @Override
    public void onChainConfirmed(ChainConfirmedSignal signal) {
        this.chainConfirmedSignal = signal;
    }

    @Override
    public void cancelPayment(CancelRequest request) {
        this.cancelRequested = true;
        this.cancelReason = request;
    }

    @Override
    public String getPaymentState() {
        return currentState;
    }

    /**
     * Runs compensation stack in LIFO order and returns a FAILED result.
     * <p>
     * Currently logs compensation steps. Actual compensation activities
     * (e.g., release FX lock, refund fiat) will be added in Phase 3.
     */
    private PaymentResult handleCancellation(PaymentRequest request) {
        currentState = "COMPENSATING";
        var reason = cancelReason != null ? cancelReason.reason() : "Cancellation requested";
        log.info("Cancellation requested for paymentId={}, reason={}, compensationSteps={}",
                request.paymentId(), reason, compensationStack.size());

        // Unwind compensation stack in LIFO order
        while (!compensationStack.isEmpty()) {
            var step = compensationStack.pop();
            log.info("Compensating step: {} for paymentId={}", step, request.paymentId());
            // Phase 3: execute compensation activities here
            // e.g., if step starts with "RELEASE_FX_LOCK:" → call fxLockActivity.releaseLock(quoteId)
        }

        currentState = "FAILED";
        return PaymentResult.failed(request.paymentId(), "Cancelled: " + reason);
    }
}
