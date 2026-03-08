package com.stablecoin.payments.onramp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Provides fallback (dev/test) implementations of outbound ports
 * via {@code @ConditionalOnMissingBean}. Real adapters are registered
 * by provider-specific configurations under
 * {@code infrastructure/provider/<name>/}.
 *
 * <p>Adapters will be added here as domain ports are defined.
 */
@Slf4j
@Configuration
public class FallbackAdaptersConfig {

    // Fallback beans will be added as domain ports are implemented.
    // Pattern: @Bean @ConditionalOnMissingBean public <Port> fallback<Port>() { ... }
}
