package com.stablecoin.payments.onramp.client;

import com.stablecoin.payments.onramp.api.CollectionRequest;
import com.stablecoin.payments.onramp.api.CollectionResponse;
import com.stablecoin.payments.onramp.api.RefundRequest;
import com.stablecoin.payments.onramp.api.RefundResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "fiat-on-ramp-service", url = "${app.services.fiat-on-ramp.url}")
public interface FiatOnRampClient {

    @PostMapping(value = "/v1/collections", consumes = "application/json", produces = "application/json")
    CollectionResponse initiateCollection(@RequestBody CollectionRequest request);

    @GetMapping(value = "/v1/collections/{collectionId}", produces = "application/json")
    CollectionResponse getCollection(@PathVariable("collectionId") UUID collectionId);

    @GetMapping(value = "/v1/collections", produces = "application/json")
    CollectionResponse getCollectionByPaymentId(@RequestParam("paymentId") UUID paymentId);

    @PostMapping(value = "/v1/collections/{collectionId}/refunds",
            consumes = "application/json", produces = "application/json")
    RefundResponse initiateRefund(@PathVariable("collectionId") UUID collectionId,
                                  @RequestBody RefundRequest request);
}
