package com.stablecoin.payments.orchestrator.domain.workflow;

import com.stablecoin.payments.orchestrator.domain.workflow.dto.CancelRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.ChainConfirmedSignal;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.FiatCollectedSignal;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Temporal workflow that orchestrates the cross-border payment saga.
 * <p>
 * Saga flow (Phase 2 scope):
 * <ol>
 *   <li>Execute compliance check (S2) — no compensation needed on failure</li>
 *   <li>Lock FX rate (S6) — no compensation needed on failure</li>
 *   <li>Phase 3 stubs: fiat collection, on-chain transfer, off-ramp</li>
 * </ol>
 * <p>
 * Workflow ID convention: {@code payment_id} (natural deduplication).
 * Task queue: {@code payment-orchestrator-queue}.
 * Workflow deadline: 30 minutes.
 */
@WorkflowInterface
public interface PaymentWorkflow {

    @WorkflowMethod
    PaymentResult executePayment(PaymentRequest request);

    @SignalMethod
    void onFiatCollected(FiatCollectedSignal signal);

    @SignalMethod
    void onChainConfirmed(ChainConfirmedSignal signal);

    @SignalMethod
    void cancelPayment(CancelRequest request);

    @QueryMethod
    String getPaymentState();
}
