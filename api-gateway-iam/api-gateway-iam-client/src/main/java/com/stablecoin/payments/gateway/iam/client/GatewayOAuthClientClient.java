package com.stablecoin.payments.gateway.iam.client;

import com.stablecoin.payments.gateway.iam.api.request.CreateOAuthClientRequest;
import com.stablecoin.payments.gateway.iam.api.response.OAuthClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "api-gateway-iam-oauth", url = "${clients.api-gateway-iam.url}")
public interface GatewayOAuthClientClient {

    @PostMapping("/v1/merchants/{merchantId}/oauth-clients")
    OAuthClientResponse createOAuthClient(
            @PathVariable UUID merchantId,
            @RequestBody CreateOAuthClientRequest request);
}
