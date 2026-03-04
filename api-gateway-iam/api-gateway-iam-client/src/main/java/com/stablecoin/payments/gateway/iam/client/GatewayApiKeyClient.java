package com.stablecoin.payments.gateway.iam.client;

import com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest;
import com.stablecoin.payments.gateway.iam.api.response.ApiKeyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "api-gateway-iam-api-key", url = "${clients.api-gateway-iam.url}")
public interface GatewayApiKeyClient {

    @PostMapping("/v1/api-keys")
    ApiKeyResponse createApiKey(@RequestBody CreateApiKeyRequest request);

    @DeleteMapping("/v1/api-keys/{keyId}")
    void revokeApiKey(@PathVariable UUID keyId);
}
