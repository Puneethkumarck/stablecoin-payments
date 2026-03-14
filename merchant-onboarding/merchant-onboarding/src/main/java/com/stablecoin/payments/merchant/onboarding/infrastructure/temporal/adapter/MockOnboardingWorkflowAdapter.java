package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.adapter;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.OnboardingWorkflowPort;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * No-op workflow adapter used when Temporal is unavailable (local, test, integration-test profiles). Registered via
 * {@code FallbackAdaptersConfig} when {@code app.fallback-adapters.enabled=true}.
 */
@Slf4j
public class MockOnboardingWorkflowAdapter implements OnboardingWorkflowPort {

    @Override
    public void startOnboarding(UUID merchantId) {
        log.info("[MOCK-WORKFLOW] Onboarding workflow start requested merchantId={} (no-op)", merchantId);
    }
}
