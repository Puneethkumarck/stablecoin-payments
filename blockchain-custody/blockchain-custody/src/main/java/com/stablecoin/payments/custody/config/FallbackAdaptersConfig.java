package com.stablecoin.payments.custody.config;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.port.ChainFeeProvider;
import com.stablecoin.payments.custody.domain.port.ChainHealthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Provides fallback (dev/test) implementations for external provider ports.
 * Each bean uses {@code @ConditionalOnMissingBean} so that production adapters
 * (activated via {@code @ConditionalOnProperty}) take precedence.
 */
@Slf4j
@Configuration
public class FallbackAdaptersConfig {

    private static final Map<String, Double> DEFAULT_FEES = Map.of(
            "base", 0.01,
            "ethereum", 2.50,
            "solana", 0.005
    );

    /**
     * Fallback health provider that returns 1.0 (healthy) for all chains.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChainHealthProvider fallbackChainHealthProvider() {
        log.info("Using fallback ChainHealthProvider (all chains healthy)");
        return (ChainId chainId) -> 1.0;
    }

    /**
     * Fallback fee provider with realistic defaults:
     * Base=0.01, Ethereum=2.50, Solana=0.005 USD.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChainFeeProvider fallbackChainFeeProvider() {
        log.info("Using fallback ChainFeeProvider (static fee estimates)");
        return (ChainId chainId, StablecoinTicker stablecoin) ->
                DEFAULT_FEES.getOrDefault(chainId.value(), 1.0);
    }
}
