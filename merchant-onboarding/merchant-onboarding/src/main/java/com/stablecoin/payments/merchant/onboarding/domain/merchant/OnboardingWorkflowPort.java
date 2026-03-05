package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import java.util.UUID;

/**
 * Port for starting the onboarding workflow. The infrastructure layer provides the Temporal adapter; tests and local
 * profiles use a no-op fallback.
 */
public interface OnboardingWorkflowPort {

    void startOnboarding(UUID merchantId);
}
