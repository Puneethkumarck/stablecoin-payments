package com.stablecoin.payments.fx.fixtures;

import com.stablecoin.payments.fx.domain.model.RateSourceType;
import com.stablecoin.payments.fx.infrastructure.persistence.entity.RateHistoryEntity;

import java.math.BigDecimal;
import java.time.Instant;

public final class RateHistoryFixtures {

    private RateHistoryFixtures() {}

    public static RateHistoryEntity aRateEntry(String from, String to, String rate, Instant recordedAt) {
        return RateHistoryEntity.builder()
                .fromCurrency(from)
                .toCurrency(to)
                .rate(new BigDecimal(rate))
                .provider("REFINITIV")
                .sourceType(RateSourceType.CEX)
                .recordedAt(recordedAt)
                .build();
    }
}
