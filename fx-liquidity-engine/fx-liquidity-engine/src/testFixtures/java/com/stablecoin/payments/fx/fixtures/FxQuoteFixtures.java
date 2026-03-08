package com.stablecoin.payments.fx.fixtures;

import com.stablecoin.payments.fx.domain.model.FxQuote;
import com.stablecoin.payments.fx.domain.model.FxQuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FxQuoteFixtures {

    public static final UUID QUOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String FROM_CURRENCY = "USD";
    public static final String TO_CURRENCY = "EUR";
    public static final BigDecimal SOURCE_AMOUNT = new BigDecimal("10000.00000000");
    public static final BigDecimal TARGET_AMOUNT = new BigDecimal("9200.00000000");
    public static final BigDecimal RATE = new BigDecimal("0.9200000000");
    public static final BigDecimal INVERSE_RATE = new BigDecimal("1.0869565217");
    public static final int FEE_BPS = 30;
    public static final BigDecimal FEE_AMOUNT = new BigDecimal("30.00000000");
    public static final String PROVIDER = "REFINITIV";

    private FxQuoteFixtures() {}

    public static FxQuote anActiveQuote() {
        return new FxQuote(
                UUID.randomUUID(), FROM_CURRENCY, TO_CURRENCY,
                SOURCE_AMOUNT, TARGET_AMOUNT,
                RATE, INVERSE_RATE,
                0, FEE_BPS, FEE_AMOUNT, PROVIDER, "REF-123",
                FxQuoteStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(300)
        );
    }

    public static FxQuote anActiveQuoteWithId(UUID quoteId) {
        return new FxQuote(
                quoteId, FROM_CURRENCY, TO_CURRENCY,
                SOURCE_AMOUNT, TARGET_AMOUNT,
                RATE, INVERSE_RATE,
                0, FEE_BPS, FEE_AMOUNT, PROVIDER, "REF-123",
                FxQuoteStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(300)
        );
    }

    public static FxQuote anExpiredQuote() {
        return new FxQuote(
                UUID.randomUUID(), FROM_CURRENCY, TO_CURRENCY,
                SOURCE_AMOUNT, TARGET_AMOUNT,
                RATE, INVERSE_RATE,
                0, FEE_BPS, FEE_AMOUNT, PROVIDER, "REF-123",
                FxQuoteStatus.EXPIRED, Instant.now().minusSeconds(60), Instant.now().minusSeconds(10)
        );
    }

    public static FxQuote aLockedQuote() {
        return new FxQuote(
                UUID.randomUUID(), FROM_CURRENCY, TO_CURRENCY,
                SOURCE_AMOUNT, TARGET_AMOUNT,
                RATE, INVERSE_RATE,
                0, FEE_BPS, FEE_AMOUNT, PROVIDER, "REF-123",
                FxQuoteStatus.LOCKED, Instant.now(), Instant.now().plusSeconds(300)
        );
    }
}
