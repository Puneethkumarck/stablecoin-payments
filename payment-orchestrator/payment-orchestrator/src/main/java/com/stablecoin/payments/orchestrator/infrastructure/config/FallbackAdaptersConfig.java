package com.stablecoin.payments.orchestrator.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Fallback adapter beans for local development and testing.
 * <p>
 * When production adapter beans are not available (e.g., Temporal workers,
 * external service clients), this configuration provides stub implementations
 * using {@code @ConditionalOnMissingBean}.
 * <p>
 * Populated as downstream adapters are implemented in later tickets.
 */
@Slf4j
@Configuration
public class FallbackAdaptersConfig {
    // Fallback beans will be added as domain ports are defined in STA-105+
}
