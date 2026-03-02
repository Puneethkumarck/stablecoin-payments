package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.workflow;

import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity.KafkaEventActivities;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity.MerchantOnboardingActivities;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.DocumentUploadedSignal;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.KybResultSignal;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.ReviewDecisionSignal;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Temporal workflow orchestrating the merchant KYB onboarding flow.
 * <p>
 * Flow:
 * <ol>
 * <li>Verify company against official registry (Companies House / SEC EDGAR)</li>
 * <li>Submit KYB check to provider (activity)</li>
 * <li>Wait for KYB result signal (7-day timeout)</li>
 * <li>If MANUAL_REVIEW — notify ops, wait for review decision (5-day timeout + escalation)</li>
 * <li>If PASSED — calculate risk tier, mark merchant as KYB passed</li>
 * <li>Publish domain events to Kafka</li>
 * </ol>
 * <p>
 * Workflow ID convention: {@code onboarding-<merchantId>}
 */
public class MerchantOnboardingWorkflowImpl implements MerchantOnboardingWorkflow {

  private static final Duration KYB_TIMEOUT = Duration.ofDays(7);
  private static final Duration REVIEW_TIMEOUT = Duration.ofDays(5);

  private final MerchantOnboardingActivities onboardingActivities = Workflow.newActivityStub(
      MerchantOnboardingActivities.class,
      ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(2))
          .setRetryOptions(
              RetryOptions.newBuilder().setMaximumAttempts(3).setInitialInterval(Duration.ofSeconds(1)).build())
          .build());

  private final KafkaEventActivities eventActivities = Workflow.newActivityStub(KafkaEventActivities.class,
      ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30))
          .setRetryOptions(
              RetryOptions.newBuilder().setMaximumAttempts(5).setInitialInterval(Duration.ofSeconds(1)).build())
          .build());

  // Signal state
  private KybResultSignal kybResult;
  private ReviewDecisionSignal reviewResult;
  private String currentStatus = "STARTED";

  private static final java.util.Set<String> ACTIVE_STATUSES = java.util.Set.of("active", "Active", "ACTIVE",
      "good standing", "Good Standing");

  @Override
  public OnboardingResult runOnboarding(UUID merchantId) {
    // Step 1: Verify company against official registry
    currentStatus = "VERIFYING_COMPANY";
    var companyStatus = onboardingActivities.verifyCompanyRegistry(merchantId);

    if ("NOT_FOUND".equals(companyStatus)) {
      currentStatus = "COMPANY_NOT_FOUND";
      onboardingActivities.rejectMerchant(merchantId, "Company not found in official registry");
      publishKybFailedEvent(merchantId, "Company not found in official registry");
      return OnboardingResult.rejected(merchantId, "Company not found in official registry");
    }

    if (!ACTIVE_STATUSES.contains(companyStatus)) {
      currentStatus = "COMPANY_NOT_ACTIVE";
      onboardingActivities.rejectMerchant(merchantId, "Company registry status is not active: " + companyStatus);
      publishKybFailedEvent(merchantId, "Company registry status: " + companyStatus);
      return OnboardingResult.rejected(merchantId, "Company registry status is not active: " + companyStatus);
    }

    // Step 2: Submit KYB check
    currentStatus = "KYB_SUBMITTING";
    var providerRef = onboardingActivities.startKyb(merchantId);
    currentStatus = "AWAITING_KYB_RESULT";

    // Step 3: Wait for KYB result signal (from webhook relay)
    boolean kybReceived = Workflow.await(KYB_TIMEOUT, () -> kybResult != null);

    if (!kybReceived) {
      currentStatus = "KYB_TIMED_OUT";
      onboardingActivities.rejectMerchant(merchantId, "KYB verification timed out after 7 days");
      publishKybFailedEvent(merchantId, "KYB verification timed out");
      return OnboardingResult.timedOut(merchantId, "KYB verification timed out after 7 days");
    }

    // Step 4: Process KYB result
    var kybStatus = kybResult.status();

    if ("FAILED".equals(kybStatus)) {
      currentStatus = "KYB_REJECTED";
      onboardingActivities.processKybResult(merchantId, kybResult);
      publishKybFailedEvent(merchantId, kybResult.reviewNotes());
      return OnboardingResult.rejected(merchantId, "KYB verification failed");
    }

    if ("MANUAL_REVIEW".equals(kybStatus)) {
      currentStatus = "MANUAL_REVIEW";
      onboardingActivities.processKybResult(merchantId, kybResult);
      onboardingActivities.notifyOpsTeam(merchantId);

      // Wait for ops review decision
      boolean reviewReceived = Workflow.await(REVIEW_TIMEOUT, () -> reviewResult != null);

      if (!reviewReceived) {
        // Escalate and wait again
        onboardingActivities.escalateReview(merchantId);
        reviewReceived = Workflow.await(REVIEW_TIMEOUT, () -> reviewResult != null);
      }

      if (!reviewReceived) {
        currentStatus = "REVIEW_TIMED_OUT";
        onboardingActivities.rejectMerchant(merchantId, "Manual review timed out");
        publishKybFailedEvent(merchantId, "Manual review timed out after escalation");
        return OnboardingResult.timedOut(merchantId, "Manual review timed out");
      }

      if ("REJECTED".equals(reviewResult.decision())) {
        currentStatus = "KYB_REJECTED";
        onboardingActivities.rejectMerchant(merchantId, reviewResult.reason());
        publishKybFailedEvent(merchantId, reviewResult.reason());
        return OnboardingResult.rejected(merchantId, reviewResult.reason());
      }

      // APPROVED — fall through to risk tier calculation
    }

    // Step 5: Calculate risk tier and mark KYB passed
    var riskSignals = kybResult.riskSignals() != null ? kybResult.riskSignals() : Map.<String, Object>of();
    var riskTier = onboardingActivities.calculateRiskTier(riskSignals);

    currentStatus = "KYB_PASSED";
    onboardingActivities.markKybPassed(merchantId, riskTier);

    // Step 6: Publish events
    publishKybPassedEvent(merchantId, kybResult, riskTier);

    currentStatus = "PENDING_APPROVAL";
    return OnboardingResult.active(merchantId, riskTier);
  }

  @Override
  public void kybResultReceived(KybResultSignal signal) {
    this.kybResult = signal;
  }

  @Override
  public void documentUploaded(DocumentUploadedSignal signal) {
    // Future: track document uploads for document-wait phase
  }

  @Override
  public void reviewDecision(ReviewDecisionSignal signal) {
    this.reviewResult = signal;
  }

  @Override
  public String getOnboardingStatus() {
    return currentStatus;
  }

  private void publishKybPassedEvent(UUID merchantId, KybResultSignal kyb, String riskTier) {
    var payload = Map.of("eventType", "merchant.kyb.passed", "merchantId", merchantId.toString(), "kybId",
        kyb.kybId() != null ? kyb.kybId().toString() : "", "provider", kyb.provider() != null ? kyb.provider() : "",
        "riskTier", riskTier);
    eventActivities.publishEvent("merchant.kyb.passed", merchantId.toString(), toJson(payload));
  }

  private void publishKybFailedEvent(UUID merchantId, String reason) {
    var payload = Map.of("eventType", "merchant.kyb.failed", "merchantId", merchantId.toString(), "reason",
        reason != null ? reason : "");
    eventActivities.publishEvent("merchant.kyb.failed", merchantId.toString(), toJson(payload));
  }

  private String toJson(Object obj) {
    try {
      return JsonMapper.builder().build().writeValueAsString(obj);
    } catch (JacksonException e) {
      throw new RuntimeException("Failed to serialize event payload", e);
    }
  }
}
