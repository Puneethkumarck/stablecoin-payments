package com.stablecoin.payments.gateway.iam.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        name = "merchant-iam",
        url = "${api-gateway-iam.merchant-iam.base-url}"
)
public interface MerchantIamClient {

    @GetMapping("/v1/.well-known/jwks.json")
    String fetchJwks();

    @GetMapping("/v1/auth/permissions/check")
    PermissionCheckResponse checkPermission(
            @RequestParam("user_id") UUID userId,
            @RequestParam("merchant_id") UUID merchantId,
            @RequestParam("permission") String permission);
}
