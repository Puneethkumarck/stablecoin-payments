package com.stablecoin.payments.gateway.iam.client;

import com.stablecoin.payments.gateway.iam.api.request.TokenRequest;
import com.stablecoin.payments.gateway.iam.api.request.TokenRevokeRequest;
import com.stablecoin.payments.gateway.iam.api.response.TokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "api-gateway-iam-token", url = "${clients.api-gateway-iam.url}")
public interface GatewayTokenClient {

    @PostMapping("/v1/auth/token")
    TokenResponse issueToken(@RequestBody TokenRequest request);

    @PostMapping("/v1/auth/revoke")
    void revokeToken(@RequestBody TokenRevokeRequest request);

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    String jwks();
}
