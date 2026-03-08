package com.stablecoin.payments.custody.domain.model;

import lombok.AccessLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents a participant (input, output, or fee) in a chain transfer.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record TransferParticipant(
        UUID participantId,
        UUID transferId,
        ParticipantType participantType,
        String address,
        UUID walletId,
        BigDecimal amount,
        String assetCode
) {

    // -- Factory Method -------------------------------------------------

    /**
     * Creates a new transfer participant.
     */
    public static TransferParticipant create(UUID transferId, ParticipantType participantType,
                                             String address, UUID walletId,
                                             BigDecimal amount, String assetCode) {
        if (transferId == null) {
            throw new IllegalArgumentException("transferId is required");
        }
        if (participantType == null) {
            throw new IllegalArgumentException("participantType is required");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (assetCode == null || assetCode.isBlank()) {
            throw new IllegalArgumentException("assetCode is required");
        }

        return TransferParticipant.builder()
                .participantId(UUID.randomUUID())
                .transferId(transferId)
                .participantType(participantType)
                .address(address)
                .walletId(walletId)
                .amount(amount)
                .assetCode(assetCode)
                .build();
    }
}
