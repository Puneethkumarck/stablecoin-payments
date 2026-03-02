package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.workflow;

import java.io.Serializable;
import java.util.UUID;

/**
 * Result returned by {@link MerchantOnboardingWorkflow#runOnboarding(UUID)}.
 */
public record OnboardingResult(String status, UUID merchantId, String riskTier,
    String failureReason) implements Serializable {

  public static OnboardingResult active(UUID merchantId, String riskTier) {
    return new OnboardingResult("ACTIVE", merchantId, riskTier, null);
  }

  public static OnboardingResult rejected(UUID merchantId, String reason) {
    return new OnboardingResult("REJECTED", merchantId, null, reason);
  }

  public static OnboardingResult timedOut(UUID merchantId, String reason) {
    return new OnboardingResult("TIMED_OUT", merchantId, null, reason);
  }
}
