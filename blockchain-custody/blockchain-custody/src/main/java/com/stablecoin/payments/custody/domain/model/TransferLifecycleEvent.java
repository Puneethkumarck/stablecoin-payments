package com.stablecoin.payments.custody.domain.model;

import lombok.AccessLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record for a state change in a chain transfer's lifecycle.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record TransferLifecycleEvent(
        UUID eventId,
        UUID transferId,
        String state,
        String participantType,
        String address,
        Instant occurredAt
) {

    // -- Factory Methods ------------------------------------------------

    /**
     * Records a simple state change event.
     */
    public static TransferLifecycleEvent record(UUID transferId, String state) {
        if (transferId == null) {
            throw new IllegalArgumentException("transferId is required");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state is required");
        }

        return TransferLifecycleEvent.builder()
                .eventId(UUID.randomUUID())
                .transferId(transferId)
                .state(state)
                .occurredAt(Instant.now())
                .build();
    }

    /**
     * Records a state change event with participant details.
     */
    public static TransferLifecycleEvent record(UUID transferId, String state,
                                                String participantType, String address) {
        if (transferId == null) {
            throw new IllegalArgumentException("transferId is required");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state is required");
        }

        return TransferLifecycleEvent.builder()
                .eventId(UUID.randomUUID())
                .transferId(transferId)
                .state(state)
                .participantType(participantType)
                .address(address)
                .occurredAt(Instant.now())
                .build();
    }
}
