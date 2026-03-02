package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Signal payload sent when a KYB provider returns a result (via webhook or polling). Received by
 * {@code MerchantOnboardingWorkflow.kybResultReceived()}.
 */
public record KybResultSignal(UUID kybId, String provider, String providerRef, String status,
    Map<String, Object> riskSignals, String reviewNotes, Instant completedAt) implements Serializable {
}
