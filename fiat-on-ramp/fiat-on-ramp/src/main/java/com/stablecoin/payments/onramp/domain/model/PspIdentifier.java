package com.stablecoin.payments.onramp.domain.model;

public record PspIdentifier(String pspId, String pspName) {

    public PspIdentifier {
        if (pspId == null || pspId.isBlank()) {
            throw new IllegalArgumentException("PSP ID is required");
        }
        if (pspName == null || pspName.isBlank()) {
            throw new IllegalArgumentException("PSP name is required");
        }
    }
}
