package com.stablecoin.payments.offramp.config;

import com.stablecoin.payments.offramp.domain.port.PayoutMonitorProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds payout monitor settings from {@code app.payout.monitor.*} to the domain port.
 */
@ConfigurationProperties(prefix = "app.payout.monitor")
public record PayoutMonitorConfig(
        boolean enabled,
        long intervalMs,
        int stuckThresholdMinutes
) implements PayoutMonitorProperties {

    public PayoutMonitorConfig {
        if (intervalMs <= 0) {
            intervalMs = 300_000;
        }
        if (stuckThresholdMinutes <= 0) {
            stuckThresholdMinutes = 120;
        }
    }
}
