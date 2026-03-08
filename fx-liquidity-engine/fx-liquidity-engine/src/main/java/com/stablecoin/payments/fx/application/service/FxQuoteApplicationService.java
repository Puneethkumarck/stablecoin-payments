package com.stablecoin.payments.fx.application.service;

import com.stablecoin.payments.fx.api.request.FxQuoteRequest;
import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.application.mapper.FxResponseMapper;
import com.stablecoin.payments.fx.domain.exception.QuoteNotFoundException;
import com.stablecoin.payments.fx.domain.exception.RateUnavailableException;
import com.stablecoin.payments.fx.domain.port.FxQuoteRepository;
import com.stablecoin.payments.fx.domain.port.RateCache;
import com.stablecoin.payments.fx.domain.port.RateProvider;
import com.stablecoin.payments.fx.domain.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FxQuoteApplicationService {

    private final RateProvider rateProvider;
    private final RateCache rateCache;
    private final QuoteService quoteService;
    private final FxQuoteRepository quoteRepository;
    private final FxResponseMapper responseMapper;

    @Transactional
    public FxQuoteResponse getQuote(FxQuoteRequest request) {
        log.info("Getting FX quote for {}:{} amount={}", request.fromCurrency(),
                request.toCurrency(), request.amount());

        // Try cache first, then provider
        var corridorRate = rateCache.get(request.fromCurrency(), request.toCurrency())
                .or(() -> {
                    log.info("Cache miss for {}:{}, fetching from provider",
                            request.fromCurrency(), request.toCurrency());
                    var providerRate = rateProvider.getRate(request.fromCurrency(), request.toCurrency());
                    providerRate.ifPresent(rate ->
                            rateCache.put(request.fromCurrency(), request.toCurrency(), rate));
                    return providerRate;
                })
                .orElseThrow(() -> {
                    // Distinguish between unsupported corridor and temporary rate unavailability
                    log.warn("No rate available for {}:{}", request.fromCurrency(), request.toCurrency());
                    return RateUnavailableException.forCorridor(
                            request.fromCurrency(), request.toCurrency());
                });

        var quote = quoteService.createQuote(
                request.fromCurrency(), request.toCurrency(),
                request.amount(), corridorRate);

        var saved = quoteRepository.save(quote);
        log.info("Quote created: quoteId={} rate={} expires={}",
                saved.quoteId(), saved.rate(), saved.expiresAt());

        return responseMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FxQuoteResponse getQuoteById(UUID quoteId) {
        var quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> QuoteNotFoundException.withId(quoteId));
        return responseMapper.toResponse(quote);
    }
}
