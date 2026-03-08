package com.stablecoin.payments.custody.infrastructure.provider.evm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "app.custody.evm")
public record EvmChainProperties(
        boolean enabled,
        Map<String, ChainRpcConfig> chains
) {

    public EvmChainProperties {
        if (chains == null) {
            chains = Map.of();
        }
    }

    public record ChainRpcConfig(
            String rpcUrl,
            long chainId,
            String usdcContractAddress,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {

        public ChainRpcConfig {
            if (connectTimeoutMs <= 0) {
                connectTimeoutMs = 5000;
            }
            if (readTimeoutMs <= 0) {
                readTimeoutMs = 10000;
            }
        }
    }
}
