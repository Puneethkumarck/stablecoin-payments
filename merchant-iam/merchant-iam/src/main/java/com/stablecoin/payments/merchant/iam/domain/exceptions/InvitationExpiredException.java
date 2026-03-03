package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class InvitationExpiredException extends RuntimeException {

    public InvitationExpiredException(UUID invitationId) {
        super("Invitation has expired: " + invitationId);
    }
}
