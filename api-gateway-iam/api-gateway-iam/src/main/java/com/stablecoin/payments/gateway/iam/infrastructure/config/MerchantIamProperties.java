package com.stablecoin.payments.gateway.iam.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api-gateway-iam.merchant-iam")
public record MerchantIamProperties(
        @NotBlank String baseUrl,
        @NotBlank String issuer,
        @NotBlank String audience,
        @Positive int jwksCacheTtlHours
) {}
