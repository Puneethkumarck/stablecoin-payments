package com.stablecoin.payments.fx.client;

import com.stablecoin.payments.fx.api.request.FxRateLockRequest;
import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "fx-engine", url = "${app.services.fx.url}")
public interface FxEngineClient {

    @GetMapping(value = "/v1/fx/quote", produces = "application/json")
    FxQuoteResponse getQuote(@RequestParam("fromCurrency") String fromCurrency,
                             @RequestParam("toCurrency") String toCurrency,
                             @RequestParam("amount") BigDecimal amount);

    @PostMapping(value = "/v1/fx/lock/{quoteId}", consumes = "application/json", produces = "application/json")
    FxRateLockResponse lockRate(@PathVariable("quoteId") UUID quoteId,
                                @RequestBody FxRateLockRequest request);

    @DeleteMapping(value = "/v1/fx/lock/{lockId}")
    void releaseLock(@PathVariable("lockId") UUID lockId);
}
