package com.stablecoin.payments.fx.infrastructure.config;

import com.stablecoin.payments.fx.domain.model.CorridorRate;
import com.stablecoin.payments.fx.domain.port.RateCache;
import com.stablecoin.payments.fx.domain.port.RateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "true")
public class FallbackAdaptersConfig {

    @Bean
    public RateProvider fallbackRateProvider() {
        log.warn("Using fallback rate provider — returning fixed rates");
        return new RateProvider() {
            private final Map<String, BigDecimal> fixedRates = Map.of(
                    "USD:EUR", new BigDecimal("0.9200000000"),
                    "EUR:USD", new BigDecimal("1.0869565217")
            );

            @Override
            public Optional<CorridorRate> getRate(String from, String to) {
                var key = from + ":" + to;
                var rate = fixedRates.get(key);
                if (rate == null) return Optional.empty();
                return Optional.of(CorridorRate.builder()
                        .fromCurrency(from)
                        .toCurrency(to)
                        .rate(rate)
                        .spreadBps(30)
                        .feeBps(30)
                        .provider("fallback")
                        .ageMs(0)
                        .build());
            }

            @Override
            public String providerName() {
                return "fallback";
            }
        };
    }

    @Bean
    public RateCache fallbackRateCache() {
        log.warn("Using in-memory fallback rate cache — not suitable for production");
        return new RateCache() {
            private final ConcurrentHashMap<String, CorridorRate> cache = new ConcurrentHashMap<>();

            @Override
            public void put(String from, String to, CorridorRate rate) {
                cache.put(from + ":" + to, rate);
            }

            @Override
            public Optional<CorridorRate> get(String from, String to) {
                return Optional.ofNullable(cache.get(from + ":" + to));
            }
        };
    }
}
