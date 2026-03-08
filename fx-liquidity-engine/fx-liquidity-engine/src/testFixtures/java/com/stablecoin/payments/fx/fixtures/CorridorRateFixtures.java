package com.stablecoin.payments.fx.fixtures;

import com.stablecoin.payments.fx.domain.model.CorridorRate;

import java.math.BigDecimal;

public final class CorridorRateFixtures {

    private CorridorRateFixtures() {}

    public static CorridorRate aUsdEurRate() {
        return CorridorRate.builder()
                .fromCurrency("USD")
                .toCurrency("EUR")
                .rate(new BigDecimal("0.9200000000"))
                .spreadBps(30)
                .feeBps(30)
                .provider("REFINITIV")
                .ageMs(1200)
                .build();
    }

    public static CorridorRate aGbpEurRate() {
        return CorridorRate.builder()
                .fromCurrency("GBP")
                .toCurrency("EUR")
                .rate(new BigDecimal("1.1600000000"))
                .spreadBps(25)
                .feeBps(25)
                .provider("REFINITIV")
                .ageMs(800)
                .build();
    }
}
