package com.stablecoin.payments.gateway.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateMerchantRequest(
        @NotNull UUID externalId,
        @NotBlank String name,
        @NotBlank @Size(min = 2, max = 3) String country,
        List<String> scopes,
        List<CorridorDto> corridors
) {
    public record CorridorDto(String sourceCountry, String targetCountry) {}
}
