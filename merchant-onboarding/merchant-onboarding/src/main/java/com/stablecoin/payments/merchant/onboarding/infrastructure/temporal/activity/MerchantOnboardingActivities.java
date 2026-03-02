package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity;

import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.KybResultSignal;
import io.temporal.activity.ActivityInterface;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Activities for the merchant onboarding workflow. Each method is a single unit of work with its own retry policy.
 * <p>
 * Note: {@code @ActivityMethod} is intentionally omitted — {@code @ActivityInterface} is sufficient and avoids
 * compatibility issues with Mockito proxies in tests.
 */
@ActivityInterface
public interface MerchantOnboardingActivities {

  /**
   * Validates the merchant's registration against an official company registry (Companies House for GB, SEC EDGAR for
   * US).
   *
   * @return the company status (e.g. "active", "dissolved") or "NOT_FOUND" / "UNSUPPORTED_COUNTRY"
   */
  String verifyCompanyRegistry(UUID merchantId);

  String startKyb(UUID merchantId);

  void processKybResult(UUID merchantId, KybResultSignal kybResult);

  String calculateRiskTier(Map<String, Object> riskSignals);

  void markKybPassed(UUID merchantId, String riskTier);

  void rejectMerchant(UUID merchantId, String reason);

  void notifyOpsTeam(UUID merchantId);

  void sendDocumentReminder(UUID merchantId, List<String> missingDocumentTypes);

  void escalateReview(UUID merchantId);
}
