package com.stablecoin.payments.fx.fixtures;

import com.stablecoin.payments.fx.domain.model.FxQuote;
import com.stablecoin.payments.fx.domain.model.FxQuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FxQuoteFixtures {

    private FxQuoteFixtures() {}

    public static FxQuote anActiveQuote() {
        return new FxQuote(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("10000.00000000"), new BigDecimal("9200.00000000"),
                new BigDecimal("0.9200000000"), new BigDecimal("1.0869565217"),
                0, 30, new BigDecimal("30.00000000"), "REFINITIV", "REF-123",
                FxQuoteStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(300)
        );
    }
}
