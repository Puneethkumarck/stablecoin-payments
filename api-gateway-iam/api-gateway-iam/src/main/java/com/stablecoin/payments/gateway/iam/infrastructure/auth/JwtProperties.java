package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "api-gateway-iam.jwt")
public record JwtProperties(
        String privateKeyBase64,
        @NotBlank String issuer,
        @NotBlank String audience,
        @Positive int accessTokenTtlSeconds
) {}
