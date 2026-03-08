package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import io.temporal.activity.ActivityInterface;

/**
 * Activity interface for locking an FX rate quote.
 * <p>
 * Implementation (STA-111) will call S6 FX & Liquidity Engine via Feign.
 * Activity stubs are configured with 30s start-to-close timeout.
 * <p>
 * Note: {@code @ActivityMethod} is intentionally omitted — {@code @ActivityInterface}
 * is sufficient and avoids issues with Mockito proxies in Temporal test environments.
 */
@ActivityInterface
public interface FxLockActivity {

    FxLockResult lockFxRate(FxLockRequest request);
}
