package com.stablecoin.payments.custody.application.config;

import com.stablecoin.payments.custody.domain.port.TransferMonitorProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-layer implementation of {@link TransferMonitorProperties}.
 * Binds to {@code app.transfer.*} YAML properties.
 */
@ConfigurationProperties(prefix = "app.transfer")
public record TransferMonitorConfig(
        int resubmitTimeoutS,
        int maxAttempts,
        int confirmingTimeoutS
) implements TransferMonitorProperties {

    public TransferMonitorConfig {
        if (resubmitTimeoutS <= 0) {
            resubmitTimeoutS = 120;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 3;
        }
        if (confirmingTimeoutS <= 0) {
            confirmingTimeoutS = 300;
        }
    }
}
