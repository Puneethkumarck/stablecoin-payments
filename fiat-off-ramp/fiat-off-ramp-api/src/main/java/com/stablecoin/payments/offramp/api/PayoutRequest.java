package com.stablecoin.payments.offramp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PayoutRequest(
        @NotNull UUID paymentId,
        @NotNull UUID correlationId,
        @NotNull UUID transferId,
        @NotBlank String payoutType,
        @NotBlank String stablecoin,
        @NotNull @Positive BigDecimal redeemedAmount,
        @NotBlank String targetCurrency,
        @NotNull @Positive BigDecimal appliedFxRate,
        @NotNull UUID recipientId,
        @NotBlank String recipientAccountHash,
        @NotBlank String paymentRail,
        @NotBlank String offRampPartnerId,
        @NotBlank String offRampPartnerName,
        String bankAccountNumber,
        String bankCode,
        String bankAccountType,
        String bankAccountCountry,
        String mobileMoneyProvider,
        String mobileMoneyPhoneNumber,
        String mobileMoneyCountry
) {}
