package com.stablecoin.payments.merchant.onboarding.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MerchantApplicationRequest(
        @NotBlank String legalName,
        @NotBlank String tradingName,
        @NotBlank String registrationNumber,
        @NotBlank @Size(min = 2, max = 2) String registrationCountry,
        @NotBlank String entityType,
        @NotBlank String websiteUrl,
        @NotBlank @Size(min = 3, max = 3) String primaryCurrency,
        @NotBlank @Email String primaryContactEmail,
        @NotBlank String primaryContactName,
        @Valid @NotNull BusinessAddressDto registeredAddress,
        @Valid @NotEmpty List<BeneficialOwnerDto> beneficialOwners,
        @NotEmpty List<String> requestedCorridors
) {

    public record BusinessAddressDto(
            @NotBlank String streetLine1,
            String streetLine2,
            @NotBlank String city,
            String stateProvince,
            @NotBlank String postcode,
            @NotBlank @Size(min = 2, max = 2) String country
    ) {}

    public record BeneficialOwnerDto(
            @NotBlank String fullName,
            @NotNull LocalDate dateOfBirth,
            @NotBlank String nationality,
            @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal ownershipPct,
            boolean isPoliticallyExposed,
            String nationalIdRef
    ) {}
}
