package com.stablecoin.payments.offramp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides fallback (dev/test) implementations of outbound ports
 * via {@code @ConditionalOnMissingBean}. Real adapters are registered
 * by provider-specific configurations under
 * {@code infrastructure/provider/<name>/}.
 */
@Slf4j
@Configuration
public class FallbackAdaptersConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
