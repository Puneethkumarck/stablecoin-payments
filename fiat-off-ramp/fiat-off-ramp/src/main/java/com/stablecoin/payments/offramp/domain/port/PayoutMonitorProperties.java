package com.stablecoin.payments.offramp.domain.port;

/**
 * Domain port for payout monitor configuration.
 * Implemented by application-layer config (e.g. {@code PayoutMonitorConfig}).
 */
public interface PayoutMonitorProperties {

    int stuckThresholdMinutes();
}
