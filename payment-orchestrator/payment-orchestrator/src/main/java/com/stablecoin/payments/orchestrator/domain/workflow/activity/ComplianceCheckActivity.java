package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import io.temporal.activity.ActivityInterface;

/**
 * Activity interface for compliance/AML/sanctions screening.
 * <p>
 * Implementation (STA-110) will call S2 Compliance service via Feign.
 * Activity stubs are configured with 30s start-to-close timeout.
 * <p>
 * Note: {@code @ActivityMethod} is intentionally omitted — {@code @ActivityInterface}
 * is sufficient and avoids issues with Mockito proxies in Temporal test environments.
 */
@ActivityInterface
public interface ComplianceCheckActivity {

    ComplianceResult checkCompliance(ComplianceRequest request);
}
