package com.stablecoin.payments.orchestrator.application.controller;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for initiating a cross-border payment.
 */
public record InitiatePaymentRequest(
        @NotNull(message = "senderId is required")
        UUID senderId,

        @NotNull(message = "recipientId is required")
        UUID recipientId,

        @NotNull(message = "sourceAmount is required")
        @DecimalMin(value = "0.01", message = "sourceAmount must be positive")
        BigDecimal sourceAmount,

        @NotBlank(message = "sourceCurrency is required")
        String sourceCurrency,

        @NotBlank(message = "targetCurrency is required")
        String targetCurrency,

        @NotBlank(message = "sourceCountry is required")
        String sourceCountry,

        @NotBlank(message = "targetCountry is required")
        String targetCountry
) {}
