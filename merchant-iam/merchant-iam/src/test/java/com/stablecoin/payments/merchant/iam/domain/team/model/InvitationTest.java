package com.stablecoin.payments.merchant.iam.domain.team.model;

import com.stablecoin.payments.merchant.iam.domain.team.model.core.InvitationStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationTest {

    private Invitation buildPendingInvitation(Instant expiresAt) {
        return Invitation.builder()
                .invitationId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .email("test@example.com")
                .emailHash("abc123")
                .fullName("Test User")
                .roleId(UUID.randomUUID())
                .invitedBy(UUID.randomUUID())
                .tokenHash("token-hash-123")
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }

    @Nested
    class Accept {

        @Test
        void transitions_pending_to_accepted() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));

            Invitation accepted = invitation.accept();

            assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(accepted.acceptedAt()).isNotNull();
        }

        @Test
        void returns_new_instance() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));

            Invitation accepted = invitation.accept();

            assertThat(accepted).isNotSameAs(invitation);
            assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        void returns_expired_status_for_expired_invitation() {
            Invitation invitation = buildPendingInvitation(Instant.now().minus(1, ChronoUnit.HOURS));

            Invitation result = invitation.accept();

            assertThat(result.status()).isEqualTo(InvitationStatus.EXPIRED);
        }

        @Test
        void rejects_already_accepted_invitation() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));
            Invitation accepted = invitation.accept();

            assertThatThrownBy(accepted::accept)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot accept");
        }

        @Test
        void rejects_revoked_invitation() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));
            Invitation revoked = invitation.revoke();

            assertThatThrownBy(revoked::accept)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Revoke {

        @Test
        void transitions_pending_to_revoked() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));

            Invitation revoked = invitation.revoke();

            assertThat(revoked.status()).isEqualTo(InvitationStatus.REVOKED);
        }

        @Test
        void rejects_already_accepted_invitation() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));
            Invitation accepted = invitation.accept();

            assertThatThrownBy(accepted::revoke)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Expire {

        @Test
        void transitions_pending_to_expired() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));

            Invitation expired = invitation.expire();

            assertThat(expired.status()).isEqualTo(InvitationStatus.EXPIRED);
        }

        @Test
        void returns_same_instance_if_already_accepted() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));
            Invitation accepted = invitation.accept();

            Invitation result = accepted.expire();

            assertThat(result.status()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(result).isSameAs(accepted);
        }
    }

    @Nested
    class ExpiryCheck {

        @Test
        void is_expired_returns_true_after_expiry() {
            Invitation invitation = buildPendingInvitation(Instant.now().minus(1, ChronoUnit.HOURS));

            assertThat(invitation.isExpired()).isTrue();
        }

        @Test
        void is_expired_returns_false_before_expiry() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));

            assertThat(invitation.isExpired()).isFalse();
        }

        @Test
        void is_pending_returns_true_when_not_expired() {
            Invitation invitation = buildPendingInvitation(Instant.now().plus(7, ChronoUnit.DAYS));

            assertThat(invitation.isPending()).isTrue();
        }

        @Test
        void is_pending_returns_false_when_expired() {
            Invitation invitation = buildPendingInvitation(Instant.now().minus(1, ChronoUnit.HOURS));

            assertThat(invitation.isPending()).isFalse();
        }
    }
}
