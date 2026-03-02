package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.workflow;

import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity.KafkaEventActivities;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity.MerchantOnboardingActivities;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.KybResultSignal;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.ReviewDecisionSignal;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("MerchantOnboardingWorkflow")
class MerchantOnboardingWorkflowTest {

  private final MerchantOnboardingActivities onboardingActivities = mock(MerchantOnboardingActivities.class);
  private final KafkaEventActivities kafkaEventActivities = mock(KafkaEventActivities.class);

  @RegisterExtension
  public TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
      .setWorkflowTypes(MerchantOnboardingWorkflowImpl.class)
      .setActivityImplementations(onboardingActivities, kafkaEventActivities).build();

  @Nested
  @DisplayName("company registry verification")
  class CompanyRegistryVerification {

    @Test
    @DisplayName("should reject when company not found in registry")
    void shouldRejectWhenCompanyNotFound(WorkflowClient workflowClient, Worker worker) {
      var merchantId = UUID.randomUUID();
      given(onboardingActivities.verifyCompanyRegistry(merchantId)).willReturn("NOT_FOUND");

      var workflow = startWorkflow(workflowClient, worker, merchantId);
      var result = getResult(workflowClient, merchantId);

      assertThat(result.status()).isEqualTo("REJECTED");
      assertThat(result.failureReason()).contains("not found in official registry");
      then(onboardingActivities).should().rejectMerchant(any(), anyString());
      then(onboardingActivities).should(never()).startKyb(any());
    }

    @Test
    @DisplayName("should reject when company is dissolved")
    void shouldRejectWhenCompanyDissolved(WorkflowClient workflowClient, Worker worker) {
      var merchantId = UUID.randomUUID();
      given(onboardingActivities.verifyCompanyRegistry(merchantId)).willReturn("dissolved");

      var workflow = startWorkflow(workflowClient, worker, merchantId);
      var result = getResult(workflowClient, merchantId);

      assertThat(result.status()).isEqualTo("REJECTED");
      assertThat(result.failureReason()).contains("not active");
      assertThat(result.failureReason()).contains("dissolved");
      then(onboardingActivities).should(never()).startKyb(any());
    }

    @Test
    @DisplayName("should proceed to KYB when company is active")
    void shouldProceedWhenCompanyActive(WorkflowClient workflowClient, Worker worker) {
      var merchantId = UUID.randomUUID();
      given(onboardingActivities.verifyCompanyRegistry(merchantId)).willReturn("active");
      given(onboardingActivities.startKyb(merchantId)).willReturn("ref-123");
      given(onboardingActivities.calculateRiskTier(any())).willReturn("LOW");

      var workflow = startWorkflow(workflowClient, worker, merchantId);

      workflow.kybResultReceived(new KybResultSignal(UUID.randomUUID(), "onfido", "check-123", "PASSED",
          Map.of("risk_score", 10), null, Instant.now()));

      var result = getResult(workflowClient, merchantId);

      assertThat(result.status()).isEqualTo("ACTIVE");
      then(onboardingActivities).should().verifyCompanyRegistry(merchantId);
      then(onboardingActivities).should().startKyb(merchantId);
    }
  }

  @Nested
  @DisplayName("KYB verification")
  class KybVerification {

    @Test
    @DisplayName("should complete onboarding when KYB passes")
    void shouldCompleteOnboardingWhenKybPasses(WorkflowClient workflowClient, Worker worker) {
      var merchantId = UUID.randomUUID();
      given(onboardingActivities.verifyCompanyRegistry(merchantId)).willReturn("active");
      given(onboardingActivities.startKyb(merchantId)).willReturn("provider-ref-123");
      given(onboardingActivities.calculateRiskTier(any())).willReturn("LOW");

      var workflow = startWorkflow(workflowClient, worker, merchantId);

      workflow.kybResultReceived(new KybResultSignal(UUID.randomUUID(), "onfido", "check-123", "PASSED",
          Map.of("risk_score", 10), null, Instant.now()));

      var result = getResult(workflowClient, merchantId);

      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.riskTier()).isEqualTo("LOW");
      assertThat(result.merchantId()).isEqualTo(merchantId);
      then(onboardingActivities).should().markKybPassed(merchantId, "LOW");
    }

    @Test
    @DisplayName("should reject when KYB fails")
    void shouldRejectWhenKybFails(WorkflowClient workflowClient, Worker worker) {
      var merchantId = UUID.randomUUID();
      given(onboardingActivities.verifyCompanyRegistry(merchantId)).willReturn("active");
      given(onboardingActivities.startKyb(merchantId)).willReturn("provider-ref-456");

      var workflow = startWorkflow(workflowClient, worker, merchantId);

      workflow.kybResultReceived(new KybResultSignal(UUID.randomUUID(), "onfido", "check-456", "FAILED", Map.of(),
          "Document mismatch", Instant.now()));

      var result = getResult(workflowClient, merchantId);

      assertThat(result.status()).isEqualTo("REJECTED");
      assertThat(result.failureReason()).isEqualTo("KYB verification failed");
      then(onboardingActivities).should().processKybResult(any(), any());
    }
  }

  @Nested
  @DisplayName("manual review")
  class ManualReview {

    @Test
    @DisplayName("should handle manual review approval")
    void shouldHandleManualReviewApproval(WorkflowClient workflowClient, Worker worker) {
      var merchantId = UUID.randomUUID();
      given(onboardingActivities.verifyCompanyRegistry(merchantId)).willReturn("active");
      given(onboardingActivities.startKyb(merchantId)).willReturn("provider-ref-789");
      given(onboardingActivities.calculateRiskTier(any())).willReturn("MEDIUM");

      var workflow = startWorkflow(workflowClient, worker, merchantId);

      workflow.kybResultReceived(new KybResultSignal(UUID.randomUUID(), "onfido", "check-789", "MANUAL_REVIEW",
          Map.of("risk_score", 35), null, Instant.now()));

      workflow.reviewDecision(new ReviewDecisionSignal("APPROVED", null, UUID.randomUUID()));

      var result = getResult(workflowClient, merchantId);

      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.riskTier()).isEqualTo("MEDIUM");
      then(onboardingActivities).should().notifyOpsTeam(merchantId);
    }

    @Test
    @DisplayName("should reject when manual review is rejected")
    void shouldRejectWhenManualReviewRejected(WorkflowClient workflowClient, Worker worker) {
      var merchantId = UUID.randomUUID();
      given(onboardingActivities.verifyCompanyRegistry(merchantId)).willReturn("active");
      given(onboardingActivities.startKyb(merchantId)).willReturn("provider-ref-abc");

      var workflow = startWorkflow(workflowClient, worker, merchantId);

      workflow.kybResultReceived(new KybResultSignal(UUID.randomUUID(), "onfido", "check-abc", "MANUAL_REVIEW",
          Map.of(), null, Instant.now()));

      workflow.reviewDecision(new ReviewDecisionSignal("REJECTED", "Suspicious documents", UUID.randomUUID()));

      var result = getResult(workflowClient, merchantId);

      assertThat(result.status()).isEqualTo("REJECTED");
      assertThat(result.failureReason()).isEqualTo("Suspicious documents");
      then(onboardingActivities).should().rejectMerchant(merchantId, "Suspicious documents");
    }
  }

  private MerchantOnboardingWorkflow startWorkflow(WorkflowClient client, Worker worker, UUID merchantId) {
    var options = WorkflowOptions.newBuilder().setWorkflowId("onboarding-" + merchantId)
        .setTaskQueue(worker.getTaskQueue()).build();
    var workflow = client.newWorkflowStub(MerchantOnboardingWorkflow.class, options);
    WorkflowClient.start(workflow::runOnboarding, merchantId);
    return workflow;
  }

  private OnboardingResult getResult(WorkflowClient client, UUID merchantId) {
    return client.newUntypedWorkflowStub("onboarding-" + merchantId).getResult(OnboardingResult.class);
  }
}
