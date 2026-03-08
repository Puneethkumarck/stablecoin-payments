package com.stablecoin.payments.fx.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FxQuoteRequest(
        @NotBlank(message = "fromCurrency is required")
        @Size(min = 3, max = 3, message = "fromCurrency must be a 3-letter ISO code")
        String fromCurrency,

        @NotBlank(message = "toCurrency is required")
        @Size(min = 3, max = 3, message = "toCurrency must be a 3-letter ISO code")
        String toCurrency,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount
) {}
