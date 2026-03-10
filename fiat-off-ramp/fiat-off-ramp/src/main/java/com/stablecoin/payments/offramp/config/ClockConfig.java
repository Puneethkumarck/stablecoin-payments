package com.stablecoin.payments.offramp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a {@link Clock} bean for time-dependent domain logic.
 * Always available — FallbackAdaptersConfig's Clock is secondary via {@code @ConditionalOnMissingBean}.
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
