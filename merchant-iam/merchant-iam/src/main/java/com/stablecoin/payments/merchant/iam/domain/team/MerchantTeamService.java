package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.EmailSenderProvider;
import com.stablecoin.payments.merchant.iam.domain.EventPublisher;
import com.stablecoin.payments.merchant.iam.domain.PermissionCacheProvider;
import com.stablecoin.payments.merchant.iam.domain.exceptions.InvitationNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.team.model.Invitation;
import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;
import com.stablecoin.payments.merchant.iam.domain.team.model.Role;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.InvitationStatus;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.Permission;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantTeamService {

    private final MerchantUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InvitationRepository invitationRepository;
    private final UserSessionRepository sessionRepository;
    private final EventPublisher<Object> eventPublisher;
    private final EmailSenderProvider emailSenderProvider;
    private final PermissionCacheProvider permissionCacheProvider;
    private final InvitationTokenGenerator tokenGenerator;
    private final EmailHasher emailHasher;

    // ── User management ─────────────────────────────────────────────────────

    @Transactional
    public InviteResult inviteUser(UUID merchantId, String email, String fullName,
                                   UUID roleId, UUID invitedBy, String merchantName) {
        var team = loadTeam(merchantId);
        var token = tokenGenerator.generateToken();
        var tokenHash = tokenGenerator.hash(token);
        var emailHash = emailHasher.hash(email);

        var result = team.inviteUser(email, emailHash, fullName, roleId, invitedBy, tokenHash);

        userRepository.save(result.user());
        invitationRepository.save(result.invitation());
        publishAll(team);

        var role = roleRepository.findById(roleId)
                .orElseThrow(() -> RoleNotFoundException.withId(roleId));

      emailSenderProvider.sendInvitationEmail(email, fullName, merchantName, token,
                result.invitation().expiresAt());

        log.info("User invited merchantId={} role={}", merchantId, role.roleName());
        return result;
    }

    @Transactional
    public MerchantUser acceptInvitation(String token, String fullName, String password) {
        var tokenHash = tokenGenerator.hash(token);
        var invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvitationNotFoundException::withToken);

        var team = loadTeam(invitation.merchantId());
        var activated = team.acceptInvitation(invitation.invitationId(), fullName, password);

        userRepository.save(activated);
        invitationRepository.save(team.getInvitations().stream()
                .filter(i -> i.invitationId().equals(invitation.invitationId()))
                .findFirst().orElseThrow());
        publishAll(team);

        log.info("Invitation accepted userId={} merchantId={}", activated.userId(), invitation.merchantId());
        return activated;
    }

    @Transactional
    public RoleChangeResult changeUserRole(UUID merchantId, UUID userId, UUID newRoleId, UUID changedBy) {
        var team = loadTeam(merchantId);
        var user = team.getUsers().stream()
                .filter(u -> u.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        var previousRole = roleRepository.findById(user.roleId())
                .orElseThrow(() -> RoleNotFoundException.withId(user.roleId()));

        var updated = team.changeUserRole(userId, newRoleId, changedBy);
        userRepository.save(updated);
        publishAll(team);
        permissionCacheProvider.evict(merchantId, userId);

        var newRole = roleRepository.findById(newRoleId)
                .orElseThrow(() -> RoleNotFoundException.withId(newRoleId));

        log.info("Role changed userId={} oldRole={} newRole={}", userId,
                previousRole.roleName(), newRole.roleName());
        return new RoleChangeResult(updated, previousRole.roleName(), newRole.roleName(), changedBy);
    }

    @Transactional
    public MerchantUser suspendUser(UUID merchantId, UUID userId, String reason, UUID suspendedBy) {
        var team = loadTeam(merchantId);
        var suspended = team.suspendUser(userId, reason, suspendedBy);
        userRepository.save(suspended);
        sessionRepository.revokeAllByUserId(userId, "user_suspended");
        publishAll(team);
        permissionCacheProvider.evict(merchantId, userId);

        log.info("User suspended userId={} merchantId={}", userId, merchantId);
        return suspended;
    }

    @Transactional
    public MerchantUser reactivateUser(UUID merchantId, UUID userId) {
        var team = loadTeam(merchantId);
        var reactivated = team.reactivateUser(userId);
        userRepository.save(reactivated);
        publishAll(team);

        log.info("User reactivated userId={} merchantId={}", userId, merchantId);
        return reactivated;
    }

    @Transactional
    public void deactivateUser(UUID merchantId, UUID userId, String reason, UUID deactivatedBy) {
        var team = loadTeam(merchantId);
        var deactivated = team.deactivateUser(userId, reason, deactivatedBy);
        userRepository.save(deactivated);
        sessionRepository.revokeAllByUserId(userId, "user_deactivated");
        publishAll(team);
        permissionCacheProvider.evict(merchantId, userId);

        log.info("User deactivated userId={} merchantId={}", userId, merchantId);
    }

    @Transactional(readOnly = true)
    public List<MerchantUser> listUsers(UUID merchantId, UserStatus statusFilter) {
        return userRepository.findByMerchantId(merchantId).stream()
                .filter(u -> statusFilter == null || u.status() == statusFilter)
                .toList();
    }

    /**
     * Lists users with their resolved roles in a single transaction.
     * Avoids lazy-load issues caused by calling findRole() outside the service transaction.
     */
    @Transactional(readOnly = true)
    public List<UserWithRole> listUsersWithRoles(UUID merchantId, UserStatus statusFilter) {
        return listUsers(merchantId, statusFilter).stream()
                .map(u -> {
                    var role = roleRepository.findById(u.roleId())
                            .orElseThrow(() -> RoleNotFoundException.withId(u.roleId()));
                    return new UserWithRole(u, role);
                })
                .toList();
    }

    /**
     * Invites a user and returns both the result and the role name in one transaction.
     * Avoids the controller needing a second findRole() call.
     */
    @Transactional
    public InviteResultWithRole inviteUserWithRole(UUID merchantId, String email, String fullName,
                                                   UUID roleId, UUID invitedBy, String merchantName) {
        var result = inviteUser(merchantId, email, fullName, roleId, invitedBy, merchantName);
        var role = roleRepository.findById(roleId)
                .orElseThrow(() -> RoleNotFoundException.withId(roleId));
        return new InviteResultWithRole(result, role.roleName());
    }

    public record UserWithRole(MerchantUser user, Role role) {}

    public record InviteResultWithRole(InviteResult inviteResult, String roleName) {}

    // ── Role management ─────────────────────────────────────────────────────

    @Transactional
    public Role createRole(UUID merchantId, String roleName, String description,
                           List<String> permissionStrings, UUID createdBy) {
        var team = loadTeam(merchantId);
        var permissions = permissionStrings.stream().map(Permission::parse).toList();
        var role = team.createCustomRole(roleName, description, permissions, createdBy);
        var saved = roleRepository.save(role);

        log.info("Role created merchantId={} roleName={}", merchantId, roleName);
        return saved;
    }

    @Transactional
    public Role updateRole(UUID merchantId, UUID roleId, List<String> permissionStrings) {
        var team = loadTeam(merchantId);
        var permissions = permissionStrings.stream().map(Permission::parse).toList();
        var updated = team.updateCustomRolePermissions(roleId, permissions);
        var saved = roleRepository.save(updated);
        permissionCacheProvider.evictAll(merchantId);

        log.info("Role updated merchantId={} roleId={}", merchantId, roleId);
        return saved;
    }

    @Transactional
    public void deleteRole(UUID merchantId, UUID roleId) {
        var team = loadTeam(merchantId);
        var deleted = team.deleteCustomRole(roleId);
        roleRepository.save(deleted);

        log.info("Role deleted merchantId={} roleId={}", merchantId, roleId);
    }

    @Transactional(readOnly = true)
    public List<Role> listRoles(UUID merchantId, boolean includeInactive) {
        return roleRepository.findByMerchantId(merchantId).stream()
                .filter(r -> includeInactive || r.active())
                .toList();
    }

    @Transactional(readOnly = true)
    public MerchantUser findUser(UUID merchantId, UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));
        if (!user.merchantId().equals(merchantId)) {
            throw UserNotFoundException.withId(userId);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public Role findRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> RoleNotFoundException.withId(roleId));
    }

    // ── Merchant lifecycle (driven by Kafka events from S11) ─────────────────

    /**
     * Seeds the 4 built-in roles and creates the first ADMIN user.
     * Called when {@code merchant.activated} is received from S11.
     * Password hash is empty — the first admin sets their password via invitation link.
     */
    @Transactional
    public MerchantUser seedRolesAndFirstAdmin(UUID merchantId, String email,
                                               String fullName, String merchantName) {
        var team = MerchantTeam.create(merchantId);
        team.seedBuiltInRoles();
        roleRepository.saveAll(team.getRoles());

        var emailHash = emailHasher.hash(email);
        var admin = team.createFirstAdmin(email, emailHash, fullName, "");
        userRepository.save(admin);
        publishAll(team);

        var token = tokenGenerator.generateToken();
        var tokenHash = tokenGenerator.hash(token);
        var expiresAt = Instant.now().plus(Duration.ofDays(7));

        var invitation = Invitation.builder()
                .invitationId(UUID.randomUUID())
                .merchantId(merchantId)
                .email(email)
                .emailHash(emailHash)
                .roleId(admin.roleId())
                .invitedBy(admin.userId())
                .tokenHash(tokenHash)
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
        invitationRepository.save(invitation);

        emailSenderProvider.sendInvitationEmail(email, fullName, merchantName, token, expiresAt);

        log.info("Seeded roles and first admin merchantId={}", merchantId);
        return admin;
    }

    /**
     * Deactivates all non-deactivated users for a merchant.
     * Called when {@code merchant.closed} is received from S11.
     */
    @Transactional
    public void deactivateAllUsers(UUID merchantId) {
        var users = userRepository.findByMerchantId(merchantId).stream()
                .filter(u -> u.status() != UserStatus.DEACTIVATED)
                .toList();
        for (var user : users) {
            var deactivated = user.deactivate();
            userRepository.save(deactivated);
            permissionCacheProvider.evict(merchantId, user.userId());
        }
        log.info("Deactivated {} users for merchantId={}", users.size(), merchantId);
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private MerchantTeam loadTeam(UUID merchantId) {
        var roles = roleRepository.findByMerchantId(merchantId);
        var users = userRepository.findByMerchantId(merchantId);
        var invitations = invitationRepository.findByMerchantId(merchantId);
        return new MerchantTeam(merchantId, roles, users, invitations);
    }

    private void publishAll(MerchantTeam team) {
        team.domainEvents().forEach(eventPublisher::publish);
        team.clearDomainEvents();
    }
}
