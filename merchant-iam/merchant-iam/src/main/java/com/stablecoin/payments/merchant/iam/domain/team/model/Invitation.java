package com.stablecoin.payments.merchant.iam.domain.team.model;

import com.stablecoin.payments.merchant.iam.domain.team.model.core.InvitationStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record Invitation(
        UUID invitationId,
        UUID merchantId,
        String email,
        String emailHash,
        String fullName,
        UUID roleId,
        UUID invitedBy,
        String tokenHash,
        InvitationStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant acceptedAt
) {

    public Invitation accept() {
        if (status != InvitationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot accept invitation in status: " + status);
        }
        if (isExpired()) {
            return toBuilder().status(InvitationStatus.EXPIRED).build();
        }
        return toBuilder()
                .status(InvitationStatus.ACCEPTED)
                .acceptedAt(Instant.now())
                .build();
    }

    public Invitation revoke() {
        if (status != InvitationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot revoke invitation in status: " + status);
        }
        return toBuilder()
                .status(InvitationStatus.REVOKED)
                .build();
    }

    public Invitation expire() {
        if (status == InvitationStatus.PENDING) {
            return toBuilder()
                    .status(InvitationStatus.EXPIRED)
                    .build();
        }
        return this;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING && !isExpired();
    }
}
