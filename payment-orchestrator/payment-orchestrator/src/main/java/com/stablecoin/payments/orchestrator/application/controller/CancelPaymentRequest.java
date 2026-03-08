package com.stablecoin.payments.orchestrator.application.controller;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for cancelling a payment.
 */
public record CancelPaymentRequest(
        @NotBlank(message = "reason is required")
        String reason
) {}
