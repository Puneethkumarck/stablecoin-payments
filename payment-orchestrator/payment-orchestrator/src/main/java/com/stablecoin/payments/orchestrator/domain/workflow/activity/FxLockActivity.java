package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import io.temporal.activity.ActivityInterface;

/**
 * Activity interface for locking an FX rate quote.
 * <p>
 * Implementation calls S6 FX &amp; Liquidity Engine via Feign to get a quote
 * and lock the rate. Includes a compensation method to release the lock
 * during saga rollback.
 * <p>
 * Note: {@code @ActivityMethod} is intentionally omitted — {@code @ActivityInterface}
 * is sufficient and avoids issues with Mockito proxies in Temporal test environments.
 */
@ActivityInterface
public interface FxLockActivity {

    FxLockResult lockFxRate(FxLockRequest request);

    void releaseLock(FxReleaseRequest request);
}
