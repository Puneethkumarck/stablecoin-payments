package com.stablecoin.payments.orchestrator.domain.workflow;

import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceCheckActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.CancelRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentRequest;
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
import java.util.UUID;

import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.FAILED;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.PASSED;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.SANCTIONS_HIT;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult.FxLockStatus.INSUFFICIENT_LIQUIDITY;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult.FxLockStatus.LOCKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("PaymentWorkflow")
class PaymentWorkflowTest {

    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RECIPIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID QUOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID CHECK_ID = UUID.fromString("00000000-0000-0000-0000-000000000088");

    private final ComplianceCheckActivity complianceActivity = mock(ComplianceCheckActivity.class);
    private final FxLockActivity fxLockActivity = mock(FxLockActivity.class);

    @RegisterExtension
    public TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(PaymentWorkflowImpl.class)
            .setActivityImplementations(complianceActivity, fxLockActivity)
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
                            QUOTE_ID, new BigDecimal("0.92"),
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
                            null, null, null, null,
                            INSUFFICIENT_LIQUIDITY, "No liquidity for USD/EUR"));

            var workflow = startWorkflow(workflowClient, worker);
            var result = getResult(workflowClient);

            var expected = PaymentResult.failed(PAYMENT_ID,
                    "FX rate lock failed: No liquidity for USD/EUR");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("cancel signal")
    class CancelSignal {

        @Test
        @DisplayName("should run compensation when cancel signal received after FX lock")
        void shouldCompensateOnCancelAfterFxLock(WorkflowClient workflowClient,
                                                  Worker worker) {
            // Set up: compliance passes, FX locks, but then cancel
            given(complianceActivity.checkCompliance(any()))
                    .willReturn(new ComplianceResult(CHECK_ID, PASSED, null));

            // FX lock will succeed, but we send cancel before it returns
            given(fxLockActivity.lockFxRate(any()))
                    .willAnswer(invocation -> {
                        // Send cancel signal during FX lock activity execution
                        var stub = workflowClient.newWorkflowStub(
                                PaymentWorkflow.class,
                                "payment-" + PAYMENT_ID);
                        stub.cancelPayment(new CancelRequest(
                                PAYMENT_ID, "Customer requested cancellation", "customer"));
                        return new FxLockResult(
                                QUOTE_ID, new BigDecimal("0.92"),
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
                            QUOTE_ID, new BigDecimal("0.92"),
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

    private PaymentRequest aPaymentRequest() {
        return new PaymentRequest(
                PAYMENT_ID,
                "idem-key-123",
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                SENDER_ID,
                RECIPIENT_ID,
                new BigDecimal("1000.00"),
                "USD",
                "EUR",
                "US",
                "DE"
        );
    }
}
