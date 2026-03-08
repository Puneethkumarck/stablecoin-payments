package com.stablecoin.payments.orchestrator.domain.workflow;

import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceCheckActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.EventPublishingActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxReleaseRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.PaymentEventRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.CancelRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;

import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.FAILED;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.PASSED;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.SANCTIONS_HIT;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult.FxLockStatus.INSUFFICIENT_LIQUIDITY;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult.FxLockStatus.LOCKED;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.CHECK_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.LOCK_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.PAYMENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.QUOTE_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.aPaymentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("PaymentWorkflow")
class PaymentWorkflowTest {

    private final ComplianceCheckActivity complianceActivity = mock(ComplianceCheckActivity.class);
    private final FxLockActivity fxLockActivity = mock(FxLockActivity.class);
    private final EventPublishingActivity eventPublishingActivity = mock(EventPublishingActivity.class);

    @RegisterExtension
    public TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(PaymentWorkflowImpl.class)
            .setActivityImplementations(complianceActivity, fxLockActivity, eventPublishingActivity)
            .build();

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should complete when compliance passes and FX locks")
        void shouldCompleteWhenCompliancePassesAndFxLocks(WorkflowClient workflowClient,
                                                          Worker worker) {
            given(complianceActivity.checkCompliance(any()))
                    .willReturn(new ComplianceResult(CHECK_ID, PASSED, null));
            given(fxLockActivity.lockFxRate(any()))
                    .willReturn(new FxLockResult(
                            LOCK_ID, QUOTE_ID, new BigDecimal("0.92"),
                            new BigDecimal("920.00"), "EUR",
                            LOCKED, null));

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.completed(
                    PAYMENT_ID, QUOTE_ID,
                    new BigDecimal("0.92"), new BigDecimal("920.00"), "EUR");

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(complianceActivity).should().checkCompliance(any(ComplianceRequest.class));
            then(fxLockActivity).should().lockFxRate(any(FxLockRequest.class));
            then(eventPublishingActivity).should(never()).publishPaymentEvent(any());
        }
    }

    @Nested
    @DisplayName("compliance failure")
    class ComplianceFailure {

        @Test
        @DisplayName("should return FAILED when compliance check fails")
        void shouldFailWhenComplianceCheckFails(WorkflowClient workflowClient,
                                                Worker worker) {
            given(complianceActivity.checkCompliance(any()))
                    .willReturn(new ComplianceResult(CHECK_ID, FAILED, "PEP match"));

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.failed(PAYMENT_ID, "Compliance check failed: PEP match");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(fxLockActivity).should(never()).lockFxRate(any());

            var expectedEvent = PaymentEventRequest.failed(
                    PAYMENT_ID, aPaymentRequest().correlationId(),
                    "COMPLIANCE_CHECK", "Compliance check failed: PEP match",
                    "COMPLIANCE_REJECTED");
            then(eventPublishingActivity).should().publishPaymentEvent(expectedEvent);
        }

        @Test
        @DisplayName("should return FAILED when compliance detects sanctions hit")
        void shouldFailOnSanctionsHit(WorkflowClient workflowClient,
                                      Worker worker) {
            given(complianceActivity.checkCompliance(any()))
                    .willReturn(new ComplianceResult(CHECK_ID, SANCTIONS_HIT,
                            "OFAC sanctions list match"));

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.failed(PAYMENT_ID,
                    "Compliance check failed: OFAC sanctions list match");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(fxLockActivity).should(never()).lockFxRate(any());

            var expectedEvent = PaymentEventRequest.failed(
                    PAYMENT_ID, aPaymentRequest().correlationId(),
                    "COMPLIANCE_CHECK", "Compliance check failed: OFAC sanctions list match",
                    "COMPLIANCE_REJECTED");
            then(eventPublishingActivity).should().publishPaymentEvent(expectedEvent);
        }
    }

    @Nested
    @DisplayName("FX lock failure")
    class FxLockFailure {

        @Test
        @DisplayName("should return FAILED when FX rate lock fails")
        void shouldFailWhenFxLockFails(WorkflowClient workflowClient,
                                       Worker worker) {
            given(complianceActivity.checkCompliance(any()))
                    .willReturn(new ComplianceResult(CHECK_ID, PASSED, null));
            given(fxLockActivity.lockFxRate(any()))
                    .willReturn(new FxLockResult(
                            null, null, null, null, null,
                            INSUFFICIENT_LIQUIDITY, "No liquidity for USD/EUR"));

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.failed(PAYMENT_ID,
                    "FX rate lock failed: No liquidity for USD/EUR");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            var expectedEvent = PaymentEventRequest.failed(
                    PAYMENT_ID, aPaymentRequest().correlationId(),
                    "FX_LOCKING", "FX rate lock failed: No liquidity for USD/EUR",
                    "FX_LOCK_REJECTED");
            then(eventPublishingActivity).should().publishPaymentEvent(expectedEvent);
        }
    }

    @Nested
    @DisplayName("cancel signal")
    class CancelSignal {

        @Test
        @DisplayName("should release FX lock when cancel signal received after FX lock succeeds")
        void shouldReleaseFxLockOnCancelAfterFxLock(WorkflowClient workflowClient,
                                                     Worker worker) {
            given(complianceActivity.checkCompliance(any()))
                    .willReturn(new ComplianceResult(CHECK_ID, PASSED, null));

            given(fxLockActivity.lockFxRate(any()))
                    .willAnswer(invocation -> {
                        var stub = workflowClient.newWorkflowStub(
                                PaymentWorkflow.class,
                                "payment-" + PAYMENT_ID);
                        stub.cancelPayment(new CancelRequest(
                                PAYMENT_ID, "Customer requested cancellation", "customer"));
                        return new FxLockResult(
                                LOCK_ID, QUOTE_ID, new BigDecimal("0.92"),
                                new BigDecimal("920.00"), "EUR",
                                LOCKED, null);
                    });

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.failed(PAYMENT_ID,
                    "Cancelled: Customer requested cancellation");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(fxLockActivity).should().releaseLock(new FxReleaseRequest(
                    LOCK_ID, PAYMENT_ID, "Customer requested cancellation"));

            var expectedEvent = PaymentEventRequest.cancelled(
                    PAYMENT_ID, aPaymentRequest().correlationId(),
                    "Customer requested cancellation");
            then(eventPublishingActivity).should().publishPaymentEvent(expectedEvent);
        }

        @Test
        @DisplayName("should not call releaseLock when cancel before FX lock")
        void shouldNotReleaseLockWhenCancelBeforeFxLock(WorkflowClient workflowClient,
                                                        Worker worker) {
            given(complianceActivity.checkCompliance(any()))
                    .willAnswer(invocation -> {
                        var stub = workflowClient.newWorkflowStub(
                                PaymentWorkflow.class,
                                "payment-" + PAYMENT_ID);
                        stub.cancelPayment(new CancelRequest(
                                PAYMENT_ID, "Changed mind", "customer"));
                        return new ComplianceResult(CHECK_ID, PASSED, null);
                    });

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.failed(PAYMENT_ID, "Cancelled: Changed mind");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(fxLockActivity).should(never()).lockFxRate(any());
            then(fxLockActivity).should(never()).releaseLock(any());

            var expectedEvent = PaymentEventRequest.cancelled(
                    PAYMENT_ID, aPaymentRequest().correlationId(), "Changed mind");
            then(eventPublishingActivity).should().publishPaymentEvent(expectedEvent);
        }

        @Test
        @DisplayName("should return FAILED even when compensation activity throws")
        void shouldReturnFailedWhenCompensationThrows(WorkflowClient workflowClient,
                                                       Worker worker) {
            given(complianceActivity.checkCompliance(any()))
                    .willReturn(new ComplianceResult(CHECK_ID, PASSED, null));

            given(fxLockActivity.lockFxRate(any()))
                    .willAnswer(invocation -> {
                        var stub = workflowClient.newWorkflowStub(
                                PaymentWorkflow.class,
                                "payment-" + PAYMENT_ID);
                        stub.cancelPayment(new CancelRequest(
                                PAYMENT_ID, "Timeout", "system"));
                        return new FxLockResult(
                                LOCK_ID, QUOTE_ID, new BigDecimal("0.92"),
                                new BigDecimal("920.00"), "EUR",
                                LOCKED, null);
                    });

            willThrow(new RuntimeException("FX engine unavailable"))
                    .given(fxLockActivity).releaseLock(any());

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.failed(PAYMENT_ID, "Cancelled: Timeout");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            // Temporal retries the activity (maxAttempts=3) before propagating the failure
            then(fxLockActivity).should(atLeast(1)).releaseLock(new FxReleaseRequest(
                    LOCK_ID, PAYMENT_ID, "Timeout"));
        }
    }

    @Nested
    @DisplayName("query method")
    class QueryMethod {

        @Test
        @DisplayName("should return current state via query")
        void shouldReturnCurrentStateViaQuery(WorkflowClient workflowClient,
                                               Worker worker) {
            given(complianceActivity.checkCompliance(any()))
                    .willReturn(new ComplianceResult(CHECK_ID, PASSED, null));
            given(fxLockActivity.lockFxRate(any()))
                    .willReturn(new FxLockResult(
                            LOCK_ID, QUOTE_ID, new BigDecimal("0.92"),
                            new BigDecimal("920.00"), "EUR",
                            LOCKED, null));

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.completed(
                    PAYMENT_ID, QUOTE_ID,
                    new BigDecimal("0.92"), new BigDecimal("920.00"), "EUR");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    private PaymentWorkflow startWorkflow(WorkflowClient client, Worker worker) {
        var options = WorkflowOptions.newBuilder()
                .setWorkflowId("payment-" + PAYMENT_ID)
                .setTaskQueue(worker.getTaskQueue())
                .build();
        var workflow = client.newWorkflowStub(PaymentWorkflow.class, options);
        WorkflowClient.start(workflow::executePayment, aPaymentRequest());
        return workflow;
    }

    private PaymentResult getResult(WorkflowClient client) {
        return client.newUntypedWorkflowStub("payment-" + PAYMENT_ID)
                .getResult(PaymentResult.class);
    }
}
