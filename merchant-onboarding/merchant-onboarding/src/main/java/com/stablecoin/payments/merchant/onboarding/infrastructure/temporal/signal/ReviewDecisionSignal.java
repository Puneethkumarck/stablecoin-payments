package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal;

import java.io.Serializable;
import java.util.UUID;

/**
 * Signal payload sent when an ops reviewer makes a manual KYB decision. Received by
 * {@code MerchantOnboardingWorkflow.reviewDecision()}.
 */
public record ReviewDecisionSignal(String decision, String reason, UUID reviewedBy) implements Serializable {
}
