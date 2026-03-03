package com.stablecoin.payments.merchant.iam.domain;

import java.time.Instant;

public interface EmailSender {

    void sendInvitationEmail(String email, String fullName, String merchantName,
                             String invitationToken, Instant expiresAt);
}
