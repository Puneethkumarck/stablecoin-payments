package com.stablecoin.payments.offramp.domain.model;

public record PartnerIdentifier(String partnerId, String partnerName) {

    public PartnerIdentifier {
        if (partnerId == null || partnerId.isBlank()) {
            throw new IllegalArgumentException("Partner ID is required");
        }
        if (partnerName == null || partnerName.isBlank()) {
            throw new IllegalArgumentException("Partner name is required");
        }
    }
}
