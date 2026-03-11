package com.stablecoin.payments.ledger.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "true")
public class FallbackAdaptersConfig {
    // Fallback adapter beans will be added as domain ports are implemented
}
