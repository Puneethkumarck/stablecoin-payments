package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.exceptions.BuiltInRoleModificationException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.InvitationExpiredException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.LastAdminException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleInUseException;
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

import java.time.Duration;
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

    /**
     * Mirrors the production flow in {@code MerchantTeamService.seedRolesAndFirstAdmin}:
     * 1. Seeds built-in roles
     * 2. Creates first admin in INVITED status (aggregate)
     * 3. Creates PENDING invitation (service layer — simulated by reconstructing the team)
     * 4. Accepts invitation → admin transitions to ACTIVE with password set
     */
    private MerchantUser createAdmin() {
        seedRoles();
        var admin = team.createFirstAdmin("admin@test.com", "admin-hash", "Admin User", "");

        // Service layer creates the invitation and saves to DB.
        // Simulate by reconstructing the team as loadTeam() would after the DB round-trip.
        var invitation = Invitation.builder()
                .invitationId(UUID.randomUUID())
                .merchantId(MERCHANT_ID)
                .email("admin@test.com")
                .emailHash("admin-hash")
                .roleId(admin.roleId())
                .invitedBy(admin.userId())
                .tokenHash("admin-token-hash")
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();

        team = new MerchantTeam(MERCHANT_ID,
                new ArrayList<>(team.getRoles()),
                new ArrayList<>(team.getUsers()),
                new ArrayList<>(List.of(invitation)));

        return team.acceptInvitation(invitation.invitationId(), "Admin User", "pwd-hash");
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
        var result = team.inviteUser(email, emailHash, fullName, roleId, invitedBy, tokenHash);
        return team.acceptInvitation(result.invitation().invitationId(), acceptName, password);
    }

    // ── Tests ───────────────────────────────────────────────

    @Nested
    class SeedRoles {

        @Test
        void creates_four_built_in_roles() {
            var roles = seedRoles();

            assertThat(roles).extracting(Role::roleName)
                    .containsExactlyInAnyOrder("ADMIN", "PAYMENTS_OPERATOR", "VIEWER", "DEVELOPER");
        }

        @Test
        void all_roles_are_builtin_and_active() {
            var roles = seedRoles();

            assertThat(roles).allMatch(Role::builtin);
            assertThat(roles).allMatch(Role::active);
        }

        @Test
        void admin_role_has_wildcard_permission() {
            seedRoles();
            var admin = findRole("ADMIN");

            assertThat(admin.permissions()).containsExactly(Permission.of("*", "*"));
        }

        @Test
        void payments_operator_has_correct_permissions() {
            seedRoles();
            var role = findRole("PAYMENTS_OPERATOR");

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
        void creates_invited_user_with_admin_role() {
            seedRoles();
            var admin = team.createFirstAdmin(
                    "admin@test.com", "admin-hash", "Admin User", "pwd-hash");

            var expected = MerchantUser.builder()
                    .status(UserStatus.INVITED)
                    .email("admin@test.com")
                    .fullName("Admin User")
                    .passwordHash("pwd-hash")
                    .authProvider(AuthProvider.LOCAL)
                    .merchantId(MERCHANT_ID)
                    .build();
            assertThat(admin).usingRecursiveComparison()
                    .comparingOnlyFields("status", "email", "fullName", "passwordHash", "authProvider", "merchantId")
                    .isEqualTo(expected);
            assertThat(admin.activatedAt()).isNull();
        }

        @Test
        void assigns_admin_role_id() {
            seedRoles();
            var adminRole = findRole("ADMIN");

            var admin = team.createFirstAdmin(
                    "admin@test.com", "admin-hash", "Admin User", "pwd-hash");

            assertThat(admin.roleId()).isEqualTo(adminRole.roleId());
        }

        @Test
        void produces_no_domain_events() {
            seedRoles();
            team.createFirstAdmin("admin@test.com", "admin-hash", "Admin User", "pwd-hash");

            assertThat(team.domainEvents()).isEmpty();
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
            var viewerRole = findRole("VIEWER");
            var invitedBy = team.getUsers().getFirst().userId();

            var result = team.inviteUser(
                    "new@test.com", "new-hash", "New User",
                    viewerRole.roleId(), invitedBy, "token-hash");

            var expectedUser = MerchantUser.builder()
                    .status(UserStatus.INVITED)
                    .email("new@test.com")
                    .roleId(viewerRole.roleId())
                    .invitedBy(invitedBy)
                    .build();
            assertThat(result.user()).usingRecursiveComparison()
                    .comparingOnlyFields("status", "email", "roleId", "invitedBy")
                    .isEqualTo(expectedUser);

            var expectedInvitation = Invitation.builder()
                    .status(InvitationStatus.PENDING)
                    .tokenHash("token-hash")
                    .build();
            assertThat(result.invitation()).usingRecursiveComparison()
                    .comparingOnlyFields("status", "tokenHash")
                    .isEqualTo(expectedInvitation);
            assertThat(result.invitation().expiresAt()).isAfter(Instant.now());
        }

        @Test
        void invitation_expires_in_7_days() {
            var viewerRole = findRole("VIEWER");
            var invitedBy = team.getUsers().getFirst().userId();

            var result = team.inviteUser(
                    "new@test.com", "new-hash", "New User",
                    viewerRole.roleId(), invitedBy, "token-hash");

            Instant expectedExpiry = Instant.now().plus(7, ChronoUnit.DAYS);
            assertThat(result.invitation().expiresAt())
                    .isBetween(expectedExpiry.minus(1, ChronoUnit.MINUTES),
                            expectedExpiry.plus(1, ChronoUnit.MINUTES));
        }

        @Test
        void produces_invited_event() {
            var viewerRole = findRole("VIEWER");
            var invitedBy = team.getUsers().getFirst().userId();

            team.inviteUser("new@test.com", "new-hash", "New User",
                    viewerRole.roleId(), invitedBy, "token-hash");

            assertThat(team.domainEvents()).singleElement()
                    .isInstanceOf(MerchantUserInvitedEvent.class)
                    .satisfies(e -> {
                        var event = (MerchantUserInvitedEvent) e;
                        assertThat(event.invitationId()).isNotNull();
                        assertThat(event).extracting("emailHash", "roleName", "invitedBy", "schemaVersion")
                                .containsExactly("new-hash", "VIEWER", invitedBy, "1.0");
                    });
        }

        @Test
        void rejects_duplicate_email() {
            var viewerRole = findRole("VIEWER");
            var invitedBy = team.getUsers().getFirst().userId();

            // admin@test.com already exists with hash "admin-hash"
            assertThatThrownBy(() -> team.inviteUser(
                    "admin@test.com", "admin-hash", "Duplicate",
                    viewerRole.roleId(), invitedBy, "token-hash"))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        void allows_reuse_of_deactivated_user_email() {
            var viewerRole = findRole("VIEWER");
            var adminUserId = team.getUsers().getFirst().userId();

            // Add a second admin so first can be deactivated
            team.createFirstAdmin("admin2@test.com", "admin2-hash", "Admin 2", "pwd2");
            team.clearDomainEvents();

            // Invite a user, accept through aggregate, then deactivate
            var invited = team.inviteUser(
                    "reuse@test.com", "reuse-hash", "Reuse User",
                    viewerRole.roleId(), adminUserId, "token1");
            var accepted = team.acceptInvitation(
                    invited.invitation().invitationId(), "Reuse User", "pwd");
            team.deactivateUser(accepted.userId(), "cleanup", adminUserId);
            team.clearDomainEvents();

            // Re-invite same email should succeed
            var reinvited = team.inviteUser(
                    "reuse@test.com", "reuse-hash", "Reuse User Again",
                    viewerRole.roleId(), adminUserId, "token2");

            assertThat(reinvited.user().status()).isEqualTo(UserStatus.INVITED);
        }

        @Test
        void rejects_invalid_role() {
            var invitedBy = team.getUsers().getFirst().userId();

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
            var viewerRole = findRole("VIEWER");
            var invitedBy = team.getUsers().getFirst().userId();
            inviteResult = team.inviteUser(
                    "invited@test.com", "invited-hash", "Invited User",
                    viewerRole.roleId(), invitedBy, "token-hash");
            team.clearDomainEvents();
        }

        @Test
        void transitions_user_to_active() {
            var accepted = team.acceptInvitation(
                    inviteResult.invitation().invitationId(),
                    "Accepted User", "new-password-hash");

            var expected = MerchantUser.builder()
                    .status(UserStatus.ACTIVE)
                    .fullName("Accepted User")
                    .passwordHash("new-password-hash")
                    .build();
            assertThat(accepted).usingRecursiveComparison()
                    .comparingOnlyFields("status", "fullName", "passwordHash")
                    .isEqualTo(expected);
            assertThat(accepted.activatedAt()).isNotNull();
        }

        @Test
        void marks_invitation_as_accepted() {
            team.acceptInvitation(
                    inviteResult.invitation().invitationId(),
                    "Accepted User", "new-password-hash");

            var invitation = team.getInvitations().stream()
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

            assertThat(team.domainEvents()).singleElement()
                    .isInstanceOf(MerchantUserActivatedEvent.class);
        }

        @Test
        void rejects_expired_invitation() {
            // Build team with an already-expired invitation
            var expiredInvitation = Invitation.builder()
                    .invitationId(UUID.randomUUID())
                    .merchantId(MERCHANT_ID)
                    .email("expired@test.com")
                    .emailHash("expired-hash")
                    .roleId(findRole("VIEWER").roleId())
                    .invitedBy(team.getUsers().getFirst().userId())
                    .tokenHash("expired-token")
                    .status(InvitationStatus.PENDING)
                    .createdAt(Instant.now().minus(8, ChronoUnit.DAYS))
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();

            var expiredUser = MerchantUser.builder()
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

            var teamWithExpired = new MerchantTeam(MERCHANT_ID,
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
            var viewerRole = findRole("VIEWER");
            var adminId = team.getUsers().getFirst().userId();
            var activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.clearDomainEvents();

            var operatorRole = findRole("PAYMENTS_OPERATOR");
            var changed = team.changeUserRole(
                    activated.userId(), operatorRole.roleId(), adminId);

            assertThat(changed.roleId()).isEqualTo(operatorRole.roleId());
            assertThat(team.domainEvents()).singleElement()
                    .isInstanceOf(MerchantUserRoleChangedEvent.class)
                    .extracting("previousRoleId", "newRoleId")
                    .containsExactly(viewerRole.roleId(), operatorRole.roleId());
        }

        @Test
        void rejects_demoting_last_admin() {
            var adminId = team.getUsers().getFirst().userId();
            var viewerRole = findRole("VIEWER");

            assertThatThrownBy(() -> team.changeUserRole(adminId, viewerRole.roleId(), adminId))
                    .isInstanceOf(LastAdminException.class);
        }

        @Test
        void allows_demoting_admin_when_others_exist() {
            var adminRole = findRole("ADMIN");
            var firstAdminId = team.getUsers().getFirst().userId();
            var secondAdmin = inviteAndAccept(
                    "admin2@test.com", "admin2-hash", "Admin 2",
                    adminRole.roleId(), firstAdminId, "token2",
                    "Admin 2", "pwd");
            team.clearDomainEvents();

            var viewerRole = findRole("VIEWER");
            var demoted = team.changeUserRole(
                    firstAdminId, viewerRole.roleId(), secondAdmin.userId());

            assertThat(demoted.roleId()).isEqualTo(viewerRole.roleId());
        }

        @Test
        void rejects_unknown_user() {
            var viewerRole = findRole("VIEWER");
            var adminId = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.changeUserRole(UUID.randomUUID(), viewerRole.roleId(), adminId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void rejects_unknown_role() {
            var adminId = team.getUsers().getFirst().userId();

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
            var viewerRole = findRole("VIEWER");
            var adminId = team.getUsers().getFirst().userId();
            var activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.clearDomainEvents();

            var suspended = team.suspendUser(
                    activated.userId(), "policy violation", adminId);

            assertThat(suspended.status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(suspended.suspendedAt()).isNotNull();
            assertThat(team.domainEvents()).singleElement()
                    .isInstanceOf(MerchantUserSuspendedEvent.class);
        }

        @Test
        void rejects_suspending_last_admin() {
            var adminId = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.suspendUser(adminId, "test", adminId))
                    .isInstanceOf(LastAdminException.class);
        }

        @Test
        void rejects_suspending_invited_user() {
            var viewerRole = findRole("VIEWER");
            var adminId = team.getUsers().getFirst().userId();
            var invited = team.inviteUser(
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
            var viewerRole = findRole("VIEWER");
            var adminId = team.getUsers().getFirst().userId();
            var activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.suspendUser(activated.userId(), "reason", adminId);
            team.clearDomainEvents();

            var reactivated = team.reactivateUser(activated.userId());

            assertThat(reactivated.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(team.domainEvents()).singleElement()
                    .isInstanceOf(MerchantUserActivatedEvent.class);
        }

        @Test
        void rejects_reactivating_active_user() {
            var viewerRole = findRole("VIEWER");
            var adminId = team.getUsers().getFirst().userId();
            var activated = inviteAndAccept(
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
            var viewerRole = findRole("VIEWER");
            var adminId = team.getUsers().getFirst().userId();
            var activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.clearDomainEvents();

            var deactivated = team.deactivateUser(
                    activated.userId(), "leaving", adminId);

            assertThat(deactivated.status()).isEqualTo(UserStatus.DEACTIVATED);
            assertThat(deactivated.deactivatedAt()).isNotNull();
            assertThat(team.domainEvents()).singleElement()
                    .isInstanceOf(MerchantUserDeactivatedEvent.class);
        }

        @Test
        void rejects_deactivating_last_admin() {
            var adminId = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.deactivateUser(adminId, "test", adminId))
                    .isInstanceOf(LastAdminException.class);
        }

        @Test
        void allows_deactivating_admin_when_others_exist() {
            var adminRole = findRole("ADMIN");
            var firstAdminId = team.getUsers().getFirst().userId();
            var secondAdmin = inviteAndAccept(
                    "admin2@test.com", "admin2-hash", "Admin 2",
                    adminRole.roleId(), firstAdminId, "token2",
                    "Admin 2", "pwd");
            team.clearDomainEvents();

            var deactivated = team.deactivateUser(
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

            var event = team.revokeAllSessions("merchant_suspended");

            assertThat(event).extracting("merchantId", "reason")
                    .containsExactly(MERCHANT_ID, "merchant_suspended");
            assertThat(team.domainEvents()).singleElement()
                    .isInstanceOf(AllSessionsRevokedEvent.class);
        }
    }

    @Nested
    class CustomRoles {

        @BeforeEach
        void setUp() {
            createAdmin();
            team.clearDomainEvents();
        }

        @Test
        void creates_custom_role() {
            var adminId = team.getUsers().getFirst().userId();
            var custom = team.createCustomRole("FINANCE_AUDITOR", "Read-only finance",
                    List.of(Permission.of("transactions", "read"), Permission.of("exports", "read")),
                    adminId);

            assertThat(custom).extracting("roleName", "builtin", "active", "createdBy")
                    .containsExactly("FINANCE_AUDITOR", false, true, adminId);
            assertThat(custom.permissions()).containsExactlyInAnyOrder(
                    Permission.of("transactions", "read"), Permission.of("exports", "read"));
            assertThat(team.getRoles()).hasSize(5); // 4 built-in + 1 custom
        }

        @Test
        void rejects_duplicate_role_name() {
            var adminId = team.getUsers().getFirst().userId();

            assertThatThrownBy(() -> team.createCustomRole("ADMIN", "Duplicate",
                    List.of(Permission.of("payments", "read")), adminId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        void deletes_custom_role_with_no_users() {
            var adminId = team.getUsers().getFirst().userId();
            var custom = team.createCustomRole("TEMP_ROLE", "Temporary",
                    List.of(Permission.of("payments", "read")), adminId);

            var deleted = team.deleteCustomRole(custom.roleId());

            assertThat(deleted.active()).isFalse();
        }

        @Test
        void rejects_deleting_builtin_role() {
            var adminRole = findRole("ADMIN");

            assertThatThrownBy(() -> team.deleteCustomRole(adminRole.roleId()))
                    .isInstanceOf(BuiltInRoleModificationException.class);
        }

        @Test
        void rejects_deleting_role_with_active_users() {
            var adminId = team.getUsers().getFirst().userId();
            var custom = team.createCustomRole("CUSTOM", "Custom",
                    List.of(Permission.of("payments", "read")), adminId);

            // Invite and accept a user with the custom role
            inviteAndAccept("user@test.com", "user-hash", "User",
                    custom.roleId(), adminId, "token", "User", "pwd");

            assertThatThrownBy(() -> team.deleteCustomRole(custom.roleId()))
                    .isInstanceOf(RoleInUseException.class);
        }

        @Test
        void updates_custom_role_permissions() {
            var adminId = team.getUsers().getFirst().userId();
            var custom = team.createCustomRole("CUSTOM", "Custom",
                    List.of(Permission.of("payments", "read")), adminId);

            var newPerms = List.of(
                    Permission.of("payments", "read"),
                    Permission.of("transactions", "read"));
            var updated = team.updateCustomRolePermissions(custom.roleId(), newPerms);

            assertThat(updated.permissions()).containsExactlyInAnyOrderElementsOf(newPerms);
        }

        @Test
        void rejects_updating_builtin_role_permissions() {
            var adminRole = findRole("ADMIN");

            assertThatThrownBy(() -> team.updateCustomRolePermissions(
                    adminRole.roleId(), List.of(Permission.of("payments", "read"))))
                    .isInstanceOf(BuiltInRoleModificationException.class);
        }
    }

    @Nested
    class RoleChangedEventEnrichment {

        @BeforeEach
        void setUp() {
            createAdmin();
            team.clearDomainEvents();
        }

        @Test
        void role_changed_event_includes_role_names() {
            var viewerRole = findRole("VIEWER");
            var adminId = team.getUsers().getFirst().userId();
            var activated = inviteAndAccept(
                    "user@test.com", "user-hash", "User",
                    viewerRole.roleId(), adminId, "token",
                    "User", "pwd");
            team.clearDomainEvents();

            var operatorRole = findRole("PAYMENTS_OPERATOR");
            team.changeUserRole(activated.userId(), operatorRole.roleId(), adminId);

            assertThat(team.domainEvents().getFirst())
                    .extracting("previousRoleName", "newRoleName", "schemaVersion")
                    .containsExactly("VIEWER", "PAYMENTS_OPERATOR", "1.0");
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
            var events = team.domainEvents();

            assertThat(events).isNotEmpty();
            assertThatThrownBy(() -> events.add("should fail"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
