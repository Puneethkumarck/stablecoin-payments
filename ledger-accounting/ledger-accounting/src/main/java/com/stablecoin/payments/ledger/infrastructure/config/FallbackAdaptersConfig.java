package com.stablecoin.payments.ledger.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "true")
public class FallbackAdaptersConfig {
    // Fallback adapter beans will be added as domain ports are implemented
}
