package com.stablecoin.payments.offramp.domain.model;

import lombok.AccessLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a stablecoin redemption for a payout order.
 * <p>
 * Created when Circle (or another redemption provider) confirms the
 * conversion from stablecoin to fiat currency.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record StablecoinRedemption(
        UUID redemptionId,
        UUID payoutId,
        StablecoinTicker stablecoin,
        BigDecimal redeemedAmount,
        BigDecimal fiatReceived,
        String fiatCurrency,
        String partner,
        String partnerReference,
        Instant redeemedAt
) {

    /**
     * Creates a new StablecoinRedemption.
     */
    public static StablecoinRedemption create(UUID payoutId, StablecoinTicker stablecoin,
                                              BigDecimal redeemedAmount, BigDecimal fiatReceived,
                                              String fiatCurrency, String partner,
                                              String partnerReference) {
        if (payoutId == null) {
            throw new IllegalArgumentException("payoutId is required");
        }
        if (stablecoin == null) {
            throw new IllegalArgumentException("stablecoin is required");
        }
        if (redeemedAmount == null || redeemedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("redeemedAmount must be positive");
        }
        if (fiatReceived == null || fiatReceived.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fiatReceived must be positive");
        }
        if (fiatCurrency == null || fiatCurrency.isBlank()) {
            throw new IllegalArgumentException("fiatCurrency is required");
        }
        if (partner == null || partner.isBlank()) {
            throw new IllegalArgumentException("partner is required");
        }
        if (partnerReference == null || partnerReference.isBlank()) {
            throw new IllegalArgumentException("partnerReference is required");
        }

        return StablecoinRedemption.builder()
                .redemptionId(UUID.randomUUID())
                .payoutId(payoutId)
                .stablecoin(stablecoin)
                .redeemedAmount(redeemedAmount)
                .fiatReceived(fiatReceived)
                .fiatCurrency(fiatCurrency)
                .partner(partner)
                .partnerReference(partnerReference)
                .redeemedAt(Instant.now())
                .build();
    }
}
