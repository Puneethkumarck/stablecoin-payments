package com.stablecoin.payments.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiateComplianceCheckRequest(
        @NotNull(message = "paymentId is required")
        UUID paymentId,

        @NotNull(message = "senderId is required")
        UUID senderId,

        @NotNull(message = "recipientId is required")
        UUID recipientId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        String currency,

        @NotBlank(message = "sourceCountry is required")
        @Size(min = 2, max = 2, message = "sourceCountry must be a 2-letter ISO code")
        String sourceCountry,

        @NotBlank(message = "targetCountry is required")
        @Size(min = 2, max = 2, message = "targetCountry must be a 2-letter ISO code")
        String targetCountry,

        @NotBlank(message = "targetCurrency is required")
        @Size(min = 3, max = 3, message = "targetCurrency must be a 3-letter ISO code")
        String targetCurrency
) {}
