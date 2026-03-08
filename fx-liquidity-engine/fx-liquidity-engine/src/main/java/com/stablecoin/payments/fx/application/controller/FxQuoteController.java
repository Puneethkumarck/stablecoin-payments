package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.api.request.FxQuoteRequest;
import com.stablecoin.payments.fx.api.request.FxRateLockRequest;
import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.fx.application.service.FxQuoteApplicationService;
import com.stablecoin.payments.fx.application.service.FxRateLockApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/fx")
@RequiredArgsConstructor
public class FxQuoteController {

    private final FxQuoteApplicationService quoteApplicationService;
    private final FxRateLockApplicationService rateLockApplicationService;

    @GetMapping("/quote")
    public FxQuoteResponse getQuote(@Valid FxQuoteRequest request) {
        log.info("GET /v1/fx/quote fromCurrency={} toCurrency={} amount={}",
                request.fromCurrency(), request.toCurrency(), request.amount());
        return quoteApplicationService.getQuote(request);
    }

    @GetMapping("/quote/{quoteId}")
    public FxQuoteResponse getQuoteById(@PathVariable UUID quoteId) {
        log.info("GET /v1/fx/quote/{}", quoteId);
        return quoteApplicationService.getQuoteById(quoteId);
    }

    @PostMapping("/lock/{quoteId}")
    public ResponseEntity<FxRateLockResponse> lockRate(@PathVariable UUID quoteId,
                                                        @Valid @RequestBody FxRateLockRequest request) {
        log.info("POST /v1/fx/lock/{} paymentId={}", quoteId, request.paymentId());
        var result = rateLockApplicationService.lockRate(quoteId, request);
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }
}
