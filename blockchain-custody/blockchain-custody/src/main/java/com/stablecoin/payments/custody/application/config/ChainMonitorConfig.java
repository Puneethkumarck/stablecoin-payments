package com.stablecoin.payments.custody.application.config;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.port.ChainConfirmationProperties;
import com.stablecoin.payments.custody.domain.port.TokenContractResolver;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Application-layer implementation of {@link ChainConfirmationProperties} and {@link TokenContractResolver}.
 * Binds to {@code app.chains.*} YAML properties.
 */
@ConfigurationProperties(prefix = "app")
public record ChainMonitorConfig(
        Map<String, ChainProperties> chains
) implements ChainConfirmationProperties, TokenContractResolver {

    public ChainMonitorConfig {
        if (chains == null) {
            chains = Map.of();
        }
    }

    @Override
    public int getMinConfirmations(String chainId) {
        var props = chains.get(chainId);
        if (props == null) {
            throw new IllegalStateException(
                    "Missing chain confirmation config for chainId=%s. Configured chains: %s"
                            .formatted(chainId, chains.keySet()));
        }
        return props.minConfirmations();
    }

    @Override
    public String resolveContract(ChainId chainId, StablecoinTicker stablecoin) {
        var props = chains.get(chainId.value());
        if (props == null) {
            throw new IllegalStateException(
                    "Missing chain config for chainId=%s. Configured chains: %s"
                            .formatted(chainId.value(), chains.keySet()));
        }
        var contracts = props.tokenContracts();
        if (contracts == null || !contracts.containsKey(stablecoin.ticker())) {
            throw new IllegalStateException(
                    "No token contract configured for chain=%s stablecoin=%s"
                            .formatted(chainId.value(), stablecoin.ticker()));
        }
        return contracts.get(stablecoin.ticker());
    }

    /**
     * Per-chain configuration properties.
     */
    public record ChainProperties(
            int minConfirmations,
            int avgFinalityS,
            String rpcUrl,
            Map<String, String> tokenContracts
    ) {

        public ChainProperties {
            if (minConfirmations <= 0) {
                throw new IllegalArgumentException(
                        "minConfirmations must be positive, got: " + minConfirmations);
            }
            if (avgFinalityS <= 0) {
                avgFinalityS = 12;
            }
            if (tokenContracts == null) {
                tokenContracts = Map.of();
            }
        }
    }
}
