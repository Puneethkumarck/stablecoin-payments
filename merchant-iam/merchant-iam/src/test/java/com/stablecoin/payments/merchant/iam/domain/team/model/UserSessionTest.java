package com.stablecoin.payments.merchant.iam.domain.team.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserSessionTest {

    private UserSession buildSession(Instant expiresAt) {
        return UserSession.builder()
                .sessionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .lastActiveAt(Instant.now())
                .revoked(false)
                .build();
    }

    @Nested
    class Revoke {

        @Test
        void returns_revoked_session() {
            UserSession session = buildSession(Instant.now().plus(1, ChronoUnit.HOURS));

            UserSession revoked = session.revoke("user_suspended");

            assertThat(revoked.revoked()).isTrue();
            assertThat(revoked.revokedAt()).isNotNull();
            assertThat(revoked.revokeReason()).isEqualTo("user_suspended");
        }

        @Test
        void original_unchanged() {
            UserSession session = buildSession(Instant.now().plus(1, ChronoUnit.HOURS));

            session.revoke("user_suspended");

            assertThat(session.revoked()).isFalse();
        }
    }

    @Nested
    class Touch {

        @Test
        void returns_session_with_updated_last_active() {
            UserSession session = buildSession(Instant.now().plus(1, ChronoUnit.HOURS));
            Instant before = session.lastActiveAt();

            UserSession touched = session.touch();

            assertThat(touched.lastActiveAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    class Validity {

        @Test
        void valid_session_is_not_revoked_and_not_expired() {
            UserSession session = buildSession(Instant.now().plus(1, ChronoUnit.HOURS));

            assertThat(session.isValid()).isTrue();
        }

        @Test
        void expired_session_is_not_valid() {
            UserSession session = buildSession(Instant.now().minus(1, ChronoUnit.HOURS));

            assertThat(session.isValid()).isFalse();
            assertThat(session.isExpired()).isTrue();
        }

        @Test
        void revoked_session_is_not_valid() {
            UserSession session = buildSession(Instant.now().plus(1, ChronoUnit.HOURS));
            UserSession revoked = session.revoke("test");

            assertThat(revoked.isValid()).isFalse();
        }
    }
}
