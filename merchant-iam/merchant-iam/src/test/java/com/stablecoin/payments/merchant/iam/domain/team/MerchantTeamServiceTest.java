package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.EmailSenderProvider;
import com.stablecoin.payments.merchant.iam.domain.EventPublisher;
import com.stablecoin.payments.merchant.iam.domain.PermissionCacheProvider;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.team.model.Invitation;
import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;
import com.stablecoin.payments.merchant.iam.domain.team.model.Role;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.AuthProvider;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.BuiltInRole;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.InvitationStatus;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.Permission;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.merchant.iam.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.merchant.iam.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MerchantTeamServiceTest {

    @Mock MerchantUserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock InvitationRepository invitationRepository;
    @Mock UserSessionRepository sessionRepository;
    @Mock EventPublisher<Object> eventPublisher;
    @Mock EmailSenderProvider emailSenderProvider;
    @Mock PermissionCacheProvider permissionCacheProvider;
    @Mock InvitationTokenGenerator tokenGenerator;
    @Mock EmailHasher emailHasher;

    @InjectMocks MerchantTeamService service;

    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ROLE_ID = UUID.randomUUID();
    private static final UUID VIEWER_ROLE_ID = UUID.randomUUID();

    private Role adminRole;
    private Role viewerRole;
    private MerchantUser adminUser;

    @BeforeEach
    void setUp() {
        adminRole = Role.builder()
                .roleId(ADMIN_ROLE_ID).merchantId(MERCHANT_ID)
                .roleName(BuiltInRole.ADMIN.name()).description("Admin")
                .builtin(true).active(true)
                .permissions(new ArrayList<>(BuiltInRole.ADMIN.defaultPermissions()))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        viewerRole = Role.builder()
                .roleId(VIEWER_ROLE_ID).merchantId(MERCHANT_ID)
                .roleName(BuiltInRole.VIEWER.name()).description("Viewer")
                .builtin(true).active(true)
                .permissions(new ArrayList<>(BuiltInRole.VIEWER.defaultPermissions()))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        adminUser = MerchantUser.builder()
                .userId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                .email("admin@test.com").emailHash("admin-hash")
                .fullName("Admin").status(UserStatus.ACTIVE)
                .roleId(ADMIN_ROLE_ID).authProvider(AuthProvider.LOCAL)
                .mfaEnabled(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .activatedAt(Instant.now())
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void givenEmptyTeam() {
        given(roleRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminRole, viewerRole));
        given(userRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminUser));
        given(invitationRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of());
    }

    // ── InviteUser ────────────────────────────────────────────────────────────

    @Nested
    class InviteUser {

        @Test
        void invites_user_saves_and_sends_email() {
            givenEmptyTeam();
            given(tokenGenerator.generateToken()).willReturn("plain-token");
            given(tokenGenerator.hash("plain-token")).willReturn("token-hash");
            given(emailHasher.hash("new@test.com")).willReturn("new-hash");
            given(roleRepository.findById(VIEWER_ROLE_ID)).willReturn(Optional.of(viewerRole));
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(invitationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedUser = MerchantUser.builder()
                    .merchantId(MERCHANT_ID)
                    .email("new@test.com").emailHash("new-hash")
                    .fullName("New User").status(UserStatus.INVITED)
                    .roleId(VIEWER_ROLE_ID).authProvider(AuthProvider.LOCAL)
                    .mfaEnabled(false)
                    .invitedBy(adminUser.userId())
                    .build();

            var expectedInvitation = Invitation.builder()
                    .merchantId(MERCHANT_ID)
                    .email("new@test.com").emailHash("new-hash")
                    .roleId(VIEWER_ROLE_ID).invitedBy(adminUser.userId())
                    .tokenHash("token-hash").status(InvitationStatus.PENDING)
                    .build();

            service.inviteUser(MERCHANT_ID, "new@test.com", "New User",
                    VIEWER_ROLE_ID, adminUser.userId(), "ACME Corp");

            then(userRepository).should().save(eqIgnoring(expectedUser, "userId"));
            then(invitationRepository).should().save(eqIgnoring(expectedInvitation, "invitationId"));
            then(emailSenderProvider).should().sendInvitationEmail(
                    eq("new@test.com"), eq("New User"), eq("ACME Corp"), eq("plain-token"), any());
            then(eventPublisher).should().publish(any());
        }

        @Test
        void throws_when_role_not_found() {
            givenEmptyTeam();
            given(tokenGenerator.generateToken()).willReturn("token");
            given(tokenGenerator.hash("token")).willReturn("hash");
            given(emailHasher.hash(anyString())).willReturn("email-hash");

            var unknownRoleId = UUID.randomUUID();

            assertThatThrownBy(() -> service.inviteUser(MERCHANT_ID, "x@test.com", "X",
                    unknownRoleId, adminUser.userId(), "Merchant"))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }

    // ── AcceptInvitation ──────────────────────────────────────────────────────

    @Nested
    class AcceptInvitation {

        @Test
        void activates_user_on_valid_token() {
            var invitedUser = MerchantUser.builder()
                    .userId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .email("invited@test.com").emailHash("invited-hash")
                    .fullName("Invited").status(UserStatus.INVITED)
                    .roleId(VIEWER_ROLE_ID).authProvider(AuthProvider.LOCAL)
                    .mfaEnabled(false)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();

            var invitation = Invitation.builder()
                    .invitationId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .email("invited@test.com").emailHash("invited-hash")
                    .roleId(VIEWER_ROLE_ID).invitedBy(adminUser.userId())
                    .tokenHash("hashed-token").status(InvitationStatus.PENDING)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(86400))
                    .build();

            given(tokenGenerator.hash("plain-token")).willReturn("hashed-token");
            given(invitationRepository.findByTokenHash("hashed-token")).willReturn(Optional.of(invitation));
            given(roleRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminRole, viewerRole));
            given(userRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminUser, invitedUser));
            given(invitationRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(invitation));
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(invitationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedUser = invitedUser.toBuilder()
                    .status(UserStatus.ACTIVE)
                    .fullName("Invited User")
                    .passwordHash("pass-hash")
                    .build();

            service.acceptInvitation("plain-token", "Invited User", "pass-hash");

            then(userRepository).should().save(eqIgnoringTimestamps(expectedUser));
        }
    }

    // ── SuspendUser ────────────────────────────────────────────────────────────

    @Nested
    class SuspendUser {

        @Test
        void suspends_user_and_revokes_sessions() {
            var viewer = MerchantUser.builder()
                    .userId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .email("viewer@test.com").emailHash("viewer-hash")
                    .fullName("Viewer").status(UserStatus.ACTIVE)
                    .roleId(VIEWER_ROLE_ID).authProvider(AuthProvider.LOCAL)
                    .mfaEnabled(false)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .activatedAt(Instant.now())
                    .build();

            given(roleRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminRole, viewerRole));
            given(userRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminUser, viewer));
            given(invitationRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of());
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedUser = viewer.toBuilder()
                    .status(UserStatus.SUSPENDED)
                    .build();

            service.suspendUser(MERCHANT_ID, viewer.userId(), "policy", adminUser.userId());

            then(userRepository).should().save(eqIgnoringTimestamps(expectedUser));
            then(sessionRepository).should().revokeAllByUserId(viewer.userId(), "user_suspended");
            then(permissionCacheProvider).should().evict(MERCHANT_ID, viewer.userId());
        }
    }

    // ── ReactivateUser ────────────────────────────────────────────────────────

    @Nested
    class ReactivateUser {

        @Test
        void reactivates_suspended_user() {
            var suspended = MerchantUser.builder()
                    .userId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .email("susp@test.com").emailHash("susp-hash")
                    .fullName("Suspended").status(UserStatus.SUSPENDED)
                    .roleId(VIEWER_ROLE_ID).authProvider(AuthProvider.LOCAL)
                    .mfaEnabled(false)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .suspendedAt(Instant.now())
                    .build();

            given(roleRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminRole, viewerRole));
            given(userRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminUser, suspended));
            given(invitationRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of());
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedUser = suspended.toBuilder()
                    .status(UserStatus.ACTIVE)
                    .suspendedAt(null)
                    .build();

            service.reactivateUser(MERCHANT_ID, suspended.userId());

            then(userRepository).should().save(eqIgnoringTimestamps(expectedUser));
        }
    }

    // ── DeactivateUser ────────────────────────────────────────────────────────

    @Nested
    class DeactivateUser {

        @Test
        void deactivates_user_revokes_sessions_and_evicts_cache() {
            var viewer = MerchantUser.builder()
                    .userId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .email("v@test.com").emailHash("v-hash")
                    .fullName("Viewer").status(UserStatus.ACTIVE)
                    .roleId(VIEWER_ROLE_ID).authProvider(AuthProvider.LOCAL)
                    .mfaEnabled(false)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .activatedAt(Instant.now())
                    .build();

            given(roleRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminRole, viewerRole));
            given(userRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminUser, viewer));
            given(invitationRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of());
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedUser = viewer.toBuilder()
                    .status(UserStatus.DEACTIVATED)
                    .build();

            service.deactivateUser(MERCHANT_ID, viewer.userId(), "leaving", adminUser.userId());

            then(userRepository).should().save(eqIgnoringTimestamps(expectedUser));
            then(sessionRepository).should().revokeAllByUserId(viewer.userId(), "user_deactivated");
            then(permissionCacheProvider).should().evict(MERCHANT_ID, viewer.userId());
        }
    }

    // ── CreateRole ────────────────────────────────────────────────────────────

    @Nested
    class CreateRole {

        @Test
        void creates_custom_role_and_saves() {
            givenEmptyTeam();
            given(roleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedRole = Role.builder()
                    .merchantId(MERCHANT_ID)
                    .roleName("AUDITOR").description("Read-only")
                    .builtin(false).active(true)
                    .permissions(List.of(
                            Permission.of("transactions", "read"),
                            Permission.of("exports", "read")))
                    .createdBy(adminUser.userId())
                    .build();

            service.createRole(MERCHANT_ID, "AUDITOR", "Read-only",
                    List.of("transactions:read", "exports:read"), adminUser.userId());

            then(roleRepository).should().save(eqIgnoring(expectedRole, "roleId"));
        }
    }

    // ── UpdateRole ────────────────────────────────────────────────────────────

    @Nested
    class UpdateRole {

        @Test
        void updates_custom_role_and_evicts_cache() {
            var customRole = Role.builder()
                    .roleId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .roleName("CUSTOM").description("Custom")
                    .builtin(false).active(true)
                    .permissions(List.of(Permission.of("payments", "read")))
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();

            given(roleRepository.findByMerchantId(MERCHANT_ID))
                    .willReturn(List.of(adminRole, viewerRole, customRole));
            given(userRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminUser));
            given(invitationRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of());
            given(roleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedRole = customRole.toBuilder()
                    .permissions(List.of(
                            Permission.of("transactions", "read"),
                            Permission.of("exports", "read")))
                    .build();

            service.updateRole(MERCHANT_ID, customRole.roleId(),
                    List.of("transactions:read", "exports:read"));

            then(roleRepository).should().save(eqIgnoringTimestamps(expectedRole));
            then(permissionCacheProvider).should().evictAll(MERCHANT_ID);
        }
    }

    // ── ChangeUserRole ────────────────────────────────────────────────────────

    @Nested
    class ChangeUserRole {

        @Test
        void changes_role_and_evicts_cache() {
            var viewer = MerchantUser.builder()
                    .userId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .email("v@test.com").emailHash("v-hash")
                    .fullName("Viewer").status(UserStatus.ACTIVE)
                    .roleId(VIEWER_ROLE_ID).authProvider(AuthProvider.LOCAL)
                    .mfaEnabled(false)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .activatedAt(Instant.now())
                    .build();

            given(roleRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminRole, viewerRole));
            given(userRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of(adminUser, viewer));
            given(invitationRepository.findByMerchantId(MERCHANT_ID)).willReturn(List.of());
            given(roleRepository.findById(VIEWER_ROLE_ID)).willReturn(Optional.of(viewerRole));
            given(roleRepository.findById(ADMIN_ROLE_ID)).willReturn(Optional.of(adminRole));
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var expectedUser = viewer.toBuilder()
                    .roleId(ADMIN_ROLE_ID)
                    .build();

            service.changeUserRole(MERCHANT_ID, viewer.userId(), ADMIN_ROLE_ID, adminUser.userId());

            then(userRepository).should().save(eqIgnoringTimestamps(expectedUser));
            then(permissionCacheProvider).should().evict(MERCHANT_ID, viewer.userId());
        }
    }

    // ── ListUsers ─────────────────────────────────────────────────────────────

    @Nested
    class ListUsers {

        @Test
        void returns_filtered_users_by_status() {
            var suspended = MerchantUser.builder()
                    .userId(UUID.randomUUID()).merchantId(MERCHANT_ID)
                    .email("s@test.com").emailHash("s-hash")
                    .fullName("Susp").status(UserStatus.SUSPENDED)
                    .roleId(VIEWER_ROLE_ID).authProvider(AuthProvider.LOCAL)
                    .mfaEnabled(false).createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();

            given(userRepository.findByMerchantId(MERCHANT_ID))
                    .willReturn(List.of(adminUser, suspended));

            var result = service.listUsers(MERCHANT_ID, UserStatus.ACTIVE);

            assertThat(result).singleElement()
                    .extracting(MerchantUser::status).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void returns_all_users_when_no_filter() {
            given(userRepository.findByMerchantId(MERCHANT_ID))
                    .willReturn(List.of(adminUser));

            var result = service.listUsers(MERCHANT_ID, null);

            assertThat(result).singleElement();
        }
    }

    // ── SeedRolesAndFirstAdmin ──────────────────────────────────────────────

    @Nested
    class SeedRolesAndFirstAdmin {

        @Test
        void should_save_invitation_record_for_first_admin() {
            given(emailHasher.hash("admin@test.com")).willReturn("admin-hash");
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(tokenGenerator.generateToken()).willReturn("invite-token");
            given(tokenGenerator.hash("invite-token")).willReturn("hashed-token");

            var expectedInvitation = Invitation.builder()
                    .merchantId(MERCHANT_ID)
                    .email("admin@test.com").emailHash("admin-hash")
                    .tokenHash("hashed-token").status(InvitationStatus.PENDING)
                    .build();

            service.seedRolesAndFirstAdmin(MERCHANT_ID, "admin@test.com", "Admin User", "ACME Corp");

            then(invitationRepository).should().save(eqIgnoring(expectedInvitation,
                    "invitationId", "roleId", "invitedBy"));
            then(emailSenderProvider).should().sendInvitationEmail(
                    eq("admin@test.com"), eq("Admin User"), eq("ACME Corp"),
                    eq("invite-token"), any(Instant.class));
        }
    }
}
