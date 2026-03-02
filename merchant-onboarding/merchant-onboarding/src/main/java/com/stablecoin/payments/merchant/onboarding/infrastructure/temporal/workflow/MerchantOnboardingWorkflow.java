package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.workflow;

import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.DocumentUploadedSignal;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.KybResultSignal;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.ReviewDecisionSignal;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.UUID;

/**
 * Temporal workflow that orchestrates the merchant onboarding lifecycle:
 * <ol>
 * <li>Submit KYB check to provider (Onfido)</li>
 * <li>Wait for KYB result (via webhook → signal)</li>
 * <li>If manual review needed — wait for ops decision (signal)</li>
 * <li>Calculate risk tier</li>
 * <li>Transition merchant to PENDING_APPROVAL</li>
 * <li>Publish domain events to Kafka</li>
 * </ol>
 * <p>
 * Workflow ID convention: {@code onboarding-<merchantId>}
 */
@WorkflowInterface
public interface MerchantOnboardingWorkflow {

  /**
   * Main workflow method — runs the full onboarding flow.
   *
   * @param merchantId
   *          the merchant to onboard (must already be in APPLIED state)
   * @return the onboarding result (ACTIVE, REJECTED, or TIMED_OUT)
   */
  @WorkflowMethod
  OnboardingResult runOnboarding(UUID merchantId);

  /**
   * Signal: KYB provider returned a result (via Onfido webhook relay).
   */
  @SignalMethod
  void kybResultReceived(KybResultSignal signal);

  /**
   * Signal: merchant uploaded a required document.
   */
  @SignalMethod
  void documentUploaded(DocumentUploadedSignal signal);

  /**
   * Signal: ops reviewer made a manual review decision (APPROVED or REJECTED).
   */
  @SignalMethod
  void reviewDecision(ReviewDecisionSignal signal);

  /**
   * Query: current onboarding status for observability.
   */
  @QueryMethod
  String getOnboardingStatus();
}
