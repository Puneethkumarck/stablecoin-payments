package com.stablecoin.payments.gateway.iam.client;

import com.stablecoin.payments.gateway.iam.api.request.CreateMerchantRequest;
import com.stablecoin.payments.gateway.iam.api.response.MerchantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "api-gateway-iam-merchant", url = "${clients.api-gateway-iam.url}")
public interface GatewayMerchantClient {

    @PostMapping("/v1/merchants")
    MerchantResponse createMerchant(@RequestBody CreateMerchantRequest request);

    @GetMapping("/v1/merchants/{merchantId}")
    MerchantResponse getMerchant(@PathVariable UUID merchantId);
}
