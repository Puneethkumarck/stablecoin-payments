package com.stablecoin.payments.custody.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Provides fallback (dev/test) implementations for external provider ports.
 * Each bean uses {@code @ConditionalOnMissingBean} so that production adapters
 * (activated via {@code @ConditionalOnProperty}) take precedence.
 *
 * <p>Domain ports (CustodyEngine, ChainRpcProvider) will be added here
 * once the domain model is scaffolded.</p>
 */
@Slf4j
@Configuration
public class FallbackAdaptersConfig {
    // Fallback beans will be added when domain ports are defined
}
