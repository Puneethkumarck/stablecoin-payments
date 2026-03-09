package com.stablecoin.payments.custody.application.config;

import com.stablecoin.payments.custody.domain.port.ChainConfirmationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Application-layer implementation of {@link ChainConfirmationProperties}.
 * Binds to {@code app.chains.*} YAML properties.
 */
@ConfigurationProperties(prefix = "app")
public record ChainMonitorConfig(
        Map<String, ChainProperties> chains
) implements ChainConfirmationProperties {

    public ChainMonitorConfig {
        if (chains == null) {
            chains = Map.of();
        }
    }

    @Override
    public int getMinConfirmations(String chainId) {
        var props = chains.get(chainId);
        return props != null ? props.minConfirmations() : 1;
    }

    /**
     * Per-chain configuration properties.
     */
    public record ChainProperties(
            int minConfirmations,
            int avgFinalityS,
            String rpcUrl
    ) {

        public ChainProperties {
            if (minConfirmations <= 0) {
                minConfirmations = 1;
            }
            if (avgFinalityS <= 0) {
                avgFinalityS = 12;
            }
        }
    }
}
