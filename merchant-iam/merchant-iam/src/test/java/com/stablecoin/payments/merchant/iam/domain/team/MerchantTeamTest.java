package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.exceptions.InvitationExpiredException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.LastAdminException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserAlreadyExistsException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.statemachine.StateMachineException;
import com.stablecoin.payments.merchant.iam.domain.team.model.Invitation;
import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;
import com.stablecoin.payments.merchant.iam.domain.team.model.Role;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.AuthProvider;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.BuiltInRole;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.InvitationStatus;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.Permission;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus;
import com.stablecoin.payments.merchant.iam.domain.team.model.events.AllSessionsRevokedEvent;
import com.stablecoin.payments.merchant.iam.domain.team.model.events.MerchantUserActivatedEvent;
import com.stablecoin.payments.merchant.iam.domain.team.model.events.MerchantUserDeactivatedEvent;
import com.stablecoin.payments.merchant.iam.domain.team.model.events.MerchantUserInvitedEvent;
import com.stablecoin.payments.merchant.iam.domain.team.model.events.MerchantUserRoleChangedEvent;
import com.stablecoin.payments.merchant.iam.domain.team.model.events.MerchantUserSuspendedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantTeamTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();

    private MerchantTeam team;

    @BeforeEach
    void setUp() {
        team = MerchantTeam.create(MERCHANT_ID);
    }

    // ── Helper to seed roles and create admin ───────────────

    private List<Role> seedRoles() {
        return team.seedBuiltInRoles();
    }

    private MerchantUser createAdmin() {
        seedRoles();
        return team.createFirstAdmin("admin@test.com", "admin-hash", "Admin User", "pwd-hash");
    }

    private Role findRole(String name) {
        return team.getRoles().stream()
                .filter(r -> r.roleName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Helper: invites a user and accepts the invitation through the aggregate,
     * returning the activated MerchantUser.
     */
    private MerchantUser inviteAndAccept(String email, String emailHash, String fullName,
                                          UUID roleId, UUID invitedBy, String tokenHash,
                                          String acceptName, String password) {
        InviteResult result = team.inviteUser(email, emailHash, fullName, roleId, invitedBy, tokenHash);
        return team.acceptInvitation(result.invitation().invitationId(), acceptName, password);
    }

    // ── Tests ───────────────────────────────────────────────

    @Nested
    class SeedRoles {

        @Test
        void creates_four_built_in_roles() {
            List<Role> roles = seedRoles();

            assertThat(roles).hasSize(4);
            assertThat(roles).extracting(Role::roleName)
                    .containsExactlyInAnyOrder("ADMIN", "PAYMENTS_OPERATOR", "VIEWER", "DEVELOPER");
        }

        @Test
        void all_roles_are_builtin_and_active() {
            List<Role> roles = seedRoles();

            assertThat(roles).allMatch(Role::builtin);
            assertThat(roles).allMatch(Role::active);
        }

        @Test
        void admin_role_has_wildcard_permission() {
            seedRoles();
            Role admin = findRole("ADMIN");

            assertThat(admin.permissions()).containsExactly(Permission.of("*", "*"));
        }

        @Test
        void payments_operator_has_correct_permissions() {
            seedRoles();
            Role role = findRole("PAYMENTS_OPERATOR");

            assertThat(role.permissions()).containsExactlyInAnyOrderElementsOf(
                    BuiltInRole.PAYMENTS_OPERATOR.defaultPermissions());
        }

        @Test
        void roles_have_merchant_id() {
            List<Role> roles = seedRoles();

            assertThat(roles).allMatch(r -> r.merchantId().equals(MERCHANT_ID));
        }
    }

    @Nested
    class CreateFirstAdmin {

        @Test
        void creates_active_user_with_admin_role() {
            seedRoles();
            MerchantUser admin = team.createFirstAdmin(
                    "admin@test.com", "admin-hash", "Admin User", "pwd-hash");

            assertThat(admin.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(admin.email()).isEqualTo("admin@test.com");
            assertThat(admin.fullName()).isEqualTo("Admin User");
            assertThat(admin.passwordHash()).isEqualTo("pwd-hash");
            assertThat(admin.authProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(admin.activatedAt()).isNotNull();
            assertThat(admin.merchantId()).isEqualTo(MERCHANT_ID);
        }

        @Test
        void assigns_admin_role_id() {
            seedRoles();
            Role adminRole = findRole("ADMIN");

            MerchantUser admin = team.createFirstAdmin(
                    "admin@test.com", "admin-hash", "Admin User", "pwd-hash");

            assertThat(admin.roleId()).isEqualTo(adminRole.roleId());
        }

        @Test
        void produces_activated_event() {
            seedRoles();
            team.createFirstAdmin("admin@test.com", "admin-hash", "Admin User", "pwd-hash");

            assertThat(team.domainEvents()).hasSize(1);
            assertThat(team.domainEvents().getFirst()).isInstanceOf(MerchantUserActivatedEvent.class);

            MerchantUserActivatedEvent event = (MerchantUserActivatedEvent) team.domainEvents().getFirst();
            assertThat(event.merchantId()).isEqualTo(MERCHANT_ID);
            assertThat(event.email()).isEqualTo("admin@test.com");
        }

        @Test
        void throws_when_no_roles_seeded() {
            assertThatThrownBy(() -> team.createFirstAdmin(
                    "admin@test.com", "admin-hash", "Admin User", "pwd-hash"))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }

    @Nested
    class InviteUser {

        @BeforeEach
        void setUp() {
            createAdmin();
            team.clearDomainEvents();
        }

        @Test
        void creates_invited_user_and_pending_invitation() {
            Role viewerRole = findRole("VIEWER");
            UUID invitedBy = team.getUsers().getFirst().userId();

            InviteResult result = team.inviteUser(
                    "new@test.com", "new-hash", "New User",
                    viewerRole.roleId(), invitedBy, "token-hash");

            assertThat(result.user().status()).isEqualTo(UserStatus.INVITED);
            assertThat(result.user().email()).isEqualTo("new@test.com");
            assertThat(result.user().roleId()).isEqualTo(viewerRole.roleId());
            assertThat(result.user().invitedBy()).isEqualTo(invitedBy);

            assertThat(result.invitation().status()).isEqualTo(InvitationStatus.PENDING);
            assertThat(result.invitation().tokenHash()).isEqualTo("token-hash");
            assertThat(result.invitation().expiresAt()).isAfter(Instant.now());
        }

        @Test
        void invitation_expires_in_7_days() {
            Role viewerRole = findRole("VIEWER");
            UUID invitedBy = team.getUsers().getFirst().userId();

            InviteResult result = team.inviteUser(
                    "new@test.com", "new-hash", "New User",
                    viewerRole.roleId(), invitedBy, "token-hash");

            Instant expectedExpiry = Instant.now().plus(7, ChronoUnit.DAYS);
            assertThat(result.invitation().expiresAt())
                    .isBetween(expectedExpiry.minus(1, ChronoUnit.MINUTES),
                            expectedExpiry.plus(1, ChronoUnit.MINUTES));
        }

        @Test
        void produces_invited_event() {
            Role viewerRole = findRole("VIEWER");
            UUID invitedBy = team.getUsers().getFirst().userId();

            team.inviteUser("new@test.com", "new-hash", "New User",
                    viewerRole.roleId(), invitedBy, "token-hash");

            assertThat(team.domainEvents()).hasSize(1);
            assertThat(team.domainEvents().getFirst()).isInstanceOf(MerchantUserInvitedEvent.class);

            MerchantUserInvitedEvent event = (MerchantUserInvitedEvent) team.domainEvents().getFirst();
            assertThat(event.email()).isEqualTo("new@test.com");
            assertThat(event.invitedBy()).isEqualTo(invitedBy);
        }

        @Test
        void rejects_duplicate_email() {
            Role viewerRole = findRole("VIEWER");
            UUID invitedBy = team.getUsers().getFirst().userId();

            // admin@test.com already exists with hash "admin-hash"
            assertThatThrownBy(() -> team.inviteUser(
                    "admin@test.com", "admin-hash", "Duplicate",
                    viewerRole.roleId(), invitedBy, "token-hash"))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        void allows_reuse_of_deactivated_user_email() {
            Role viewerRole = findRole("VIEWER");
            UUID adminUserId = team.getUsers().getFirst().userId();

            // Add a second admin so first can be deactivated
            team.createFirstAdmin("admin2@test.com", "admin2-hash", "Admin 2", "pwd2");
            team.clearDomainEvents();

            // Invite a user, accept through aggregate, then deactivate
            InviteResult invited = team.inviteUser(
                    "reuse@test.com", "reuse-hash", "Reuse User",
                    viewerRole.roleId(), adminUserId, "token1");
            MerchantUser accepted = team.acceptInvitation(
                    invited.invitation().invitationId(), "Reuse User", "pwd");
            team.deactivateUser(accepted.userId(), "cleanup", adminUserId);
            team.clearDomainEvents();

            // Re-invite same email should succeed
            InviteResult reinvited = team.inviteUser(
                    "reuse@test.com", "reuse-hash", "Reuse User Again",
                    viewerRole.roleId(), adminUserId, "token2");

            assertThat(reinvited.user().status()).isEqualTo(UserStatus.INVITED);
        }

        @Test
        void rejects_invalid_role() {
            UUID invitedBy = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.inviteUser(
                    "new@test.com", "new-hash", "New User",
                    UUID.randomUUID(), invitedBy, "token-hash"))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }

    @Nested
    class AcceptInvitation {

        private InviteResult inviteResult;

        @BeforeEach
        void setUp() {
            createAdmin();
            Role viewerRole = findRole("VIEWER");
            UUID invitedBy = team.getUsers().getFirst().userId();
            inviteResult = team.inviteUser(
                    "invited@test.com", "invited-hash", "Invited User",
                    viewerRole.roleId(), invitedBy, "token-hash");
            team.clearDomainEvents();
        }

        @Test
        void transitions_user_to_active() {
            MerchantUser accepted = team.acceptInvitation(
                    inviteResult.invitation().invitationId(),
                    "Accepted User", "new-password-hash");

            assertThat(accepted.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(accepted.fullName()).isEqualTo("Accepted User");
            assertThat(accepted.passwordHash()).isEqualTo("new-password-hash");
            assertThat(accepted.activatedAt()).isNotNull();
        }

        @Test
        void marks_invitation_as_accepted() {
            team.acceptInvitation(
                    inviteResult.invitation().invitationId(),
                    "Accepted User", "new-password-hash");

            Invitation invitation = team.getInvitations().stream()
                    .filter(i -> i.invitationId().equals(inviteResult.invitation().invitationId()))
                    .findFirst()
                    .orElseThrow();

            assertThat(invitation.status()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(invitation.acceptedAt()).isNotNull();
        }

        @Test
        void produces_activated_event() {
            team.acceptInvitation(
                    inviteResult.invitation().invitationId(),
                    "Accepted User", "new-password-hash");

            assertThat(team.domainEvents()).hasSize(1);
            assertThat(team.domainEvents().getFirst()).isInstanceOf(MerchantUserActivatedEvent.class);
        }

        @Test
        void rejects_expired_invitation() {
            // Build team with an already-expired invitation
            Invitation expiredInvitation = Invitation.builder()
                    .invitationId(UUID.randomUUID())
                    .merchantId(MERCHANT_ID)
                    .email("expired@test.com")
                    .emailHash("expired-hash")
                    .fullName("Expired User")
                    .roleId(findRole("VIEWER").roleId())
                    .invitedBy(team.getUsers().getFirst().userId())
                    .tokenHash("expired-token")
                    .status(InvitationStatus.PENDING)
                    .createdAt(Instant.now().minus(8, ChronoUnit.DAYS))
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();

            MerchantUser expiredUser = MerchantUser.builder()
                    .userId(UUID.randomUUID())
                    .merchantId(MERCHANT_ID)
                    .email("expired@test.com")
                    .emailHash("expired-hash")
                    .fullName("Expired User")
                    .status(UserStatus.INVITED)
                    .roleId(findRole("VIEWER").roleId())
                    .authProvider(AuthProvider.LOCAL)
                    .mfaEnabled(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            MerchantTeam teamWithExpired = new MerchantTeam(MERCHANT_ID,
                    new ArrayList<>(team.getRoles()),
                    new ArrayList<>(List.of(team.getUsers().getFirst(), expiredUser)),
                    new ArrayList<>(List.of(expiredInvitation)));

            assertThatThrownBy(() -> teamWithExpired.acceptInvitation(
                    expiredInvitation.invitationId(), "Name", "pass"))
                    .isInstanceOf(InvitationExpiredException.class);
        }
    }

    @Nested
    class ChangeUserRole {

        @BeforeEach
        void setUp() {
            createAdmin();
            team.clearDomainEvents();
        }

        @Test
        void changes_role_and_produces_event() {
            Role viewerRole = findRole("VIEWER");
            UUID adminId = team.getUsers().getFirst().userId();
            MerchantUser activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.clearDomainEvents();

            Role operatorRole = findRole("PAYMENTS_OPERATOR");
            MerchantUser changed = team.changeUserRole(
                    activated.userId(), operatorRole.roleId(), adminId);

            assertThat(changed.roleId()).isEqualTo(operatorRole.roleId());
            assertThat(team.domainEvents()).hasSize(1);
            assertThat(team.domainEvents().getFirst()).isInstanceOf(MerchantUserRoleChangedEvent.class);

            MerchantUserRoleChangedEvent event = (MerchantUserRoleChangedEvent) team.domainEvents().getFirst();
            assertThat(event.previousRoleId()).isEqualTo(viewerRole.roleId());
            assertThat(event.newRoleId()).isEqualTo(operatorRole.roleId());
        }

        @Test
        void rejects_demoting_last_admin() {
            UUID adminId = team.getUsers().getFirst().userId();
            Role viewerRole = findRole("VIEWER");

            assertThatThrownBy(() -> team.changeUserRole(adminId, viewerRole.roleId(), adminId))
                    .isInstanceOf(LastAdminException.class);
        }

        @Test
        void allows_demoting_admin_when_others_exist() {
            Role adminRole = findRole("ADMIN");
            UUID firstAdminId = team.getUsers().getFirst().userId();
            MerchantUser secondAdmin = inviteAndAccept(
                    "admin2@test.com", "admin2-hash", "Admin 2",
                    adminRole.roleId(), firstAdminId, "token2",
                    "Admin 2", "pwd");
            team.clearDomainEvents();

            Role viewerRole = findRole("VIEWER");
            MerchantUser demoted = team.changeUserRole(
                    firstAdminId, viewerRole.roleId(), secondAdmin.userId());

            assertThat(demoted.roleId()).isEqualTo(viewerRole.roleId());
        }

        @Test
        void rejects_unknown_user() {
            Role viewerRole = findRole("VIEWER");
            UUID adminId = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.changeUserRole(UUID.randomUUID(), viewerRole.roleId(), adminId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void rejects_unknown_role() {
            UUID adminId = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.changeUserRole(adminId, UUID.randomUUID(), adminId))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }

    @Nested
    class SuspendUser {

        @BeforeEach
        void setUp() {
            createAdmin();
            team.clearDomainEvents();
        }

        @Test
        void suspends_active_user() {
            Role viewerRole = findRole("VIEWER");
            UUID adminId = team.getUsers().getFirst().userId();
            MerchantUser activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.clearDomainEvents();

            MerchantUser suspended = team.suspendUser(
                    activated.userId(), "policy violation", adminId);

            assertThat(suspended.status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(suspended.suspendedAt()).isNotNull();
            assertThat(team.domainEvents()).hasSize(1);
            assertThat(team.domainEvents().getFirst()).isInstanceOf(MerchantUserSuspendedEvent.class);
        }

        @Test
        void rejects_suspending_last_admin() {
            UUID adminId = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.suspendUser(adminId, "test", adminId))
                    .isInstanceOf(LastAdminException.class);
        }

        @Test
        void rejects_suspending_invited_user() {
            Role viewerRole = findRole("VIEWER");
            UUID adminId = team.getUsers().getFirst().userId();
            InviteResult invited = team.inviteUser(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token");
            team.clearDomainEvents();

            assertThatThrownBy(() -> team.suspendUser(invited.user().userId(), "test", adminId))
                    .isInstanceOf(StateMachineException.class);
        }
    }

    @Nested
    class ReactivateUser {

        @BeforeEach
        void setUp() {
            createAdmin();
            team.clearDomainEvents();
        }

        @Test
        void reactivates_suspended_user() {
            Role viewerRole = findRole("VIEWER");
            UUID adminId = team.getUsers().getFirst().userId();
            MerchantUser activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.suspendUser(activated.userId(), "reason", adminId);
            team.clearDomainEvents();

            MerchantUser reactivated = team.reactivateUser(activated.userId());

            assertThat(reactivated.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(team.domainEvents()).hasSize(1);
            assertThat(team.domainEvents().getFirst()).isInstanceOf(MerchantUserActivatedEvent.class);
        }

        @Test
        void rejects_reactivating_active_user() {
            Role viewerRole = findRole("VIEWER");
            UUID adminId = team.getUsers().getFirst().userId();
            MerchantUser activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.clearDomainEvents();

            assertThatThrownBy(() -> team.reactivateUser(activated.userId()))
                    .isInstanceOf(StateMachineException.class);
        }
    }

    @Nested
    class DeactivateUser {

        @BeforeEach
        void setUp() {
            createAdmin();
            team.clearDomainEvents();
        }

        @Test
        void deactivates_active_user() {
            Role viewerRole = findRole("VIEWER");
            UUID adminId = team.getUsers().getFirst().userId();
            MerchantUser activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.clearDomainEvents();

            MerchantUser deactivated = team.deactivateUser(
                    activated.userId(), "leaving", adminId);

            assertThat(deactivated.status()).isEqualTo(UserStatus.DEACTIVATED);
            assertThat(deactivated.deactivatedAt()).isNotNull();
            assertThat(team.domainEvents()).hasSize(1);
            assertThat(team.domainEvents().getFirst()).isInstanceOf(MerchantUserDeactivatedEvent.class);
        }

        @Test
        void rejects_deactivating_last_admin() {
            UUID adminId = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.deactivateUser(adminId, "test", adminId))
                    .isInstanceOf(LastAdminException.class);
        }

        @Test
        void allows_deactivating_admin_when_others_exist() {
            Role adminRole = findRole("ADMIN");
            UUID firstAdminId = team.getUsers().getFirst().userId();
            MerchantUser secondAdmin = inviteAndAccept(
                    "admin2@test.com", "admin2-hash", "Admin 2",
                    adminRole.roleId(), firstAdminId, "token2",
                    "Admin 2", "pwd");
            team.clearDomainEvents();

            MerchantUser deactivated = team.deactivateUser(
                    firstAdminId, "leaving", secondAdmin.userId());

            assertThat(deactivated.status()).isEqualTo(UserStatus.DEACTIVATED);
        }
    }

    @Nested
    class RevokeAllSessions {

        @Test
        void produces_revoked_event() {
            createAdmin();
            team.clearDomainEvents();

            AllSessionsRevokedEvent event = team.revokeAllSessions("merchant_suspended");

            assertThat(event.merchantId()).isEqualTo(MERCHANT_ID);
            assertThat(event.reason()).isEqualTo("merchant_suspended");
            assertThat(team.domainEvents()).hasSize(1);
            assertThat(team.domainEvents().getFirst()).isInstanceOf(AllSessionsRevokedEvent.class);
        }
    }

    @Nested
    class DomainEvents {

        @Test
        void clear_domain_events_empties_list() {
            createAdmin(); // produces event

            assertThat(team.domainEvents()).isNotEmpty();

            team.clearDomainEvents();

            assertThat(team.domainEvents()).isEmpty();
        }

        @Test
        void domain_events_returns_immutable_copy() {
            createAdmin();
            List<Object> events = team.domainEvents();

            assertThat(events).hasSize(1);
            assertThatThrownBy(() -> events.add("should fail"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
