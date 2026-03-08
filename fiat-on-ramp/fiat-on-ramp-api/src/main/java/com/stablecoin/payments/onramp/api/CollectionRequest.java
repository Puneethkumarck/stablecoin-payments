package com.stablecoin.payments.onramp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CollectionRequest(
        @NotNull UUID paymentId,
        @NotNull UUID correlationId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String paymentRailType,
        @NotBlank String railCountry,
        @NotBlank String railCurrency,
        @NotBlank String pspId,
        @NotBlank String pspName,
        @NotBlank String senderAccountHash,
        @NotBlank String senderBankCode,
        @NotBlank String senderAccountType,
        @NotBlank String senderAccountCountry
) {}
