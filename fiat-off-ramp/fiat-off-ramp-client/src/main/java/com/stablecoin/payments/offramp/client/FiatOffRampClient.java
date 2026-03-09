package com.stablecoin.payments.offramp.client;

import com.stablecoin.payments.offramp.api.PayoutRequest;
import com.stablecoin.payments.offramp.api.PayoutResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "fiat-off-ramp-service", url = "${app.services.fiat-off-ramp.url}")
public interface FiatOffRampClient {

    @PostMapping(value = "/v1/payouts", consumes = "application/json", produces = "application/json")
    PayoutResponse initiatePayout(@RequestBody PayoutRequest request);

    @GetMapping(value = "/v1/payouts/{payoutId}", produces = "application/json")
    PayoutResponse getPayout(@PathVariable("payoutId") UUID payoutId);
}
