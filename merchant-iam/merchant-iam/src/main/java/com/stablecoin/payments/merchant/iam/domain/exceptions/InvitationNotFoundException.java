package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException(UUID invitationId) {
        super("Invitation not found: " + invitationId);
    }

    public InvitationNotFoundException(String tokenHash) {
        super("Invitation not found for token");
    }
}
