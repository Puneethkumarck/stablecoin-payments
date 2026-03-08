package com.stablecoin.payments.fx.domain.service;

import com.stablecoin.payments.fx.domain.model.CorridorRate;
import com.stablecoin.payments.fx.domain.model.FxQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class QuoteService {

    private static final int DEFAULT_QUOTE_TTL_SECONDS = 10;

    public FxQuote createQuote(String fromCurrency, String toCurrency,
                                BigDecimal sourceAmount, CorridorRate corridorRate) {
        log.info("Creating FX quote for {}:{} amount={}", fromCurrency, toCurrency, sourceAmount);

        if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Source amount must be positive");
        }

        var quote = FxQuote.create(fromCurrency, toCurrency, sourceAmount,
                corridorRate, DEFAULT_QUOTE_TTL_SECONDS);

        log.info("Created quote={} rate={} target={} expires={}",
                quote.quoteId(), quote.rate(), quote.targetAmount(), quote.expiresAt());
        return quote;
    }

    public FxQuote expireQuote(FxQuote quote) {
        log.info("Expiring quote={}", quote.quoteId());
        return quote.expire();
    }
}
