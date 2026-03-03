package com.stablecoin.payments.merchant.iam.domain.team.model;

import com.stablecoin.payments.merchant.iam.domain.statemachine.StateMachineException;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.AuthProvider;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus.ACTIVE;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus.DEACTIVATED;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus.INVITED;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantUserTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();

    private MerchantUser buildUser(UserStatus status) {
        return MerchantUser.builder()
                .userId(UUID.randomUUID())
                .merchantId(MERCHANT_ID)
                .email("test@example.com")
                .emailHash("abc123")
                .fullName("Test User")
                .status(status)
                .roleId(ROLE_ID)
                .authProvider(AuthProvider.LOCAL)
                .mfaEnabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    class AcceptInvitation {

        @Test
        void transitions_invited_to_active() {
            MerchantUser user = buildUser(INVITED);

            MerchantUser accepted = user.acceptInvitation("Full Name", "hashedPassword");

            assertThat(accepted.status()).isEqualTo(ACTIVE);
            assertThat(accepted.fullName()).isEqualTo("Full Name");
            assertThat(accepted.passwordHash()).isEqualTo("hashedPassword");
            assertThat(accepted.activatedAt()).isNotNull();
        }

        @Test
        void returns_new_instance() {
            MerchantUser user = buildUser(INVITED);

            MerchantUser accepted = user.acceptInvitation("Name", "pass");

            assertThat(accepted).isNotSameAs(user);
            assertThat(user.status()).isEqualTo(INVITED); // original unchanged
        }

        @Test
        void rejects_if_already_active() {
            MerchantUser user = buildUser(ACTIVE);

            assertThatThrownBy(() -> user.acceptInvitation("Name", "pass"))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        void rejects_if_suspended() {
            MerchantUser user = buildUser(SUSPENDED);

            assertThatThrownBy(() -> user.acceptInvitation("Name", "pass"))
                    .isInstanceOf(StateMachineException.class);
        }
    }

    @Nested
    class Suspend {

        @Test
        void transitions_active_to_suspended() {
            MerchantUser user = buildUser(ACTIVE);

            MerchantUser suspended = user.suspend();

            assertThat(suspended.status()).isEqualTo(SUSPENDED);
            assertThat(suspended.suspendedAt()).isNotNull();
        }

        @Test
        void rejects_if_invited() {
            MerchantUser user = buildUser(INVITED);

            assertThatThrownBy(user::suspend)
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        void rejects_if_deactivated() {
            MerchantUser user = buildUser(DEACTIVATED);

            assertThatThrownBy(user::suspend)
                    .isInstanceOf(StateMachineException.class);
        }
    }

    @Nested
    class Reactivate {

        @Test
        void transitions_suspended_to_active() {
            MerchantUser user = buildUser(SUSPENDED);

            MerchantUser reactivated = user.reactivate();

            assertThat(reactivated.status()).isEqualTo(ACTIVE);
            assertThat(reactivated.suspendedAt()).isNull();
        }

        @Test
        void rejects_if_active() {
            MerchantUser user = buildUser(ACTIVE);

            assertThatThrownBy(user::reactivate)
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        void rejects_if_deactivated() {
            MerchantUser user = buildUser(DEACTIVATED);

            assertThatThrownBy(user::reactivate)
                    .isInstanceOf(StateMachineException.class);
        }
    }

    @Nested
    class Deactivate {

        @Test
        void transitions_active_to_deactivated() {
            MerchantUser user = buildUser(ACTIVE);

            MerchantUser deactivated = user.deactivate();

            assertThat(deactivated.status()).isEqualTo(DEACTIVATED);
            assertThat(deactivated.deactivatedAt()).isNotNull();
        }

        @Test
        void transitions_suspended_to_deactivated() {
            MerchantUser user = buildUser(SUSPENDED);

            MerchantUser deactivated = user.deactivate();

            assertThat(deactivated.status()).isEqualTo(DEACTIVATED);
            assertThat(deactivated.deactivatedAt()).isNotNull();
        }

        @Test
        void rejects_if_invited() {
            MerchantUser user = buildUser(INVITED);

            assertThatThrownBy(user::deactivate)
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        void deactivated_is_terminal() {
            MerchantUser user = buildUser(DEACTIVATED);

            assertThatThrownBy(user::deactivate)
                    .isInstanceOf(StateMachineException.class);
            assertThatThrownBy(user::reactivate)
                    .isInstanceOf(StateMachineException.class);
            assertThatThrownBy(user::suspend)
                    .isInstanceOf(StateMachineException.class);
        }
    }

    @Nested
    class ChangeRole {

        @Test
        void returns_new_instance_with_updated_role() {
            MerchantUser user = buildUser(ACTIVE);
            UUID newRoleId = UUID.randomUUID();

            MerchantUser changed = user.changeRole(newRoleId);

            assertThat(changed.roleId()).isEqualTo(newRoleId);
            assertThat(user.roleId()).isEqualTo(ROLE_ID); // original unchanged
        }
    }

    @Nested
    class Mfa {

        @Test
        void enable_mfa_sets_secret_ref() {
            MerchantUser user = buildUser(ACTIVE);

            MerchantUser withMfa = user.enableMfa("vault:secret/mfa/abc");

            assertThat(withMfa.mfaEnabled()).isTrue();
            assertThat(withMfa.mfaSecretRef()).isEqualTo("vault:secret/mfa/abc");
        }

        @Test
        void disable_mfa_clears_secret_ref() {
            MerchantUser user = buildUser(ACTIVE);
            MerchantUser withMfa = user.enableMfa("vault:secret/mfa/abc");

            MerchantUser withoutMfa = withMfa.disableMfa();

            assertThat(withoutMfa.mfaEnabled()).isFalse();
            assertThat(withoutMfa.mfaSecretRef()).isNull();
        }
    }

    @Nested
    class IsAdmin {

        @Test
        void returns_true_when_role_matches() {
            MerchantUser user = buildUser(ACTIVE);

            assertThat(user.isAdmin(ROLE_ID)).isTrue();
        }

        @Test
        void returns_false_when_role_differs() {
            MerchantUser user = buildUser(ACTIVE);

            assertThat(user.isAdmin(UUID.randomUUID())).isFalse();
        }
    }
}
