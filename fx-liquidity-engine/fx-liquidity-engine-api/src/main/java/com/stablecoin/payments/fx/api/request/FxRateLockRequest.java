package com.stablecoin.payments.fx.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FxRateLockRequest(
        @NotNull(message = "paymentId is required")
        UUID paymentId,

        @NotNull(message = "correlationId is required")
        UUID correlationId,

        @NotBlank(message = "sourceCountry is required")
        @Size(min = 2, max = 2, message = "sourceCountry must be a 2-letter ISO code")
        String sourceCountry,

        @NotBlank(message = "targetCountry is required")
        @Size(min = 2, max = 2, message = "targetCountry must be a 2-letter ISO code")
        String targetCountry
) {}
