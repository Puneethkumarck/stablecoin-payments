package com.stablecoin.payments.ledger.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Provides fallback (dev/test) implementations of external provider ports.
 * Activated only when {@code app.fallback-adapters.enabled=true}.
 * <p>
 * Note: Outbox-based event publishers are infrastructure adapters (DB-dependent)
 * and are NOT included here — they are always active when a database is present.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "true")
public class FallbackAdaptersConfig {
}
