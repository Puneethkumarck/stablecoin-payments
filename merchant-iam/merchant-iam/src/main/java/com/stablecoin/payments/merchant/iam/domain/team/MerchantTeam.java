package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.exceptions.BuiltInRoleModificationException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.InvitationExpiredException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.LastAdminException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleInUseException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserAlreadyExistsException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserNotFoundException;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the Merchant Team bounded context.
 * Manages users, roles, invitations and enforces all team invariants.
 * Zero Spring/JPA dependencies — pure domain logic.
 */
public class MerchantTeam {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final UUID merchantId;
    private final List<Role> roles;
    private final List<MerchantUser> users;
    private final List<Invitation> invitations;
    private final List<Object> domainEvents = new ArrayList<>();

    public MerchantTeam(UUID merchantId,
                        List<Role> roles,
                        List<MerchantUser> users,
                        List<Invitation> invitations) {
        this.merchantId = merchantId;
        this.roles = new ArrayList<>(roles);
        this.users = new ArrayList<>(users);
        this.invitations = new ArrayList<>(invitations);
    }

    public static MerchantTeam create(UUID merchantId) {
        return new MerchantTeam(merchantId, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    // ── Queries ─────────────────────────────────────────────

    public UUID getMerchantId() {
        return merchantId;
    }

    public List<Role> getRoles() {
        return List.copyOf(roles);
    }

    public List<MerchantUser> getUsers() {
        return List.copyOf(users);
    }

    public List<Invitation> getInvitations() {
        return List.copyOf(invitations);
    }

    // ── Commands ────────────────────────────────────────────

    /**
     * Seeds the 4 built-in roles for a newly activated merchant.
     */
    public List<Role> seedBuiltInRoles() {
        var seeded = new ArrayList<Role>();
        for (BuiltInRole builtIn : BuiltInRole.values()) {
            var role = Role.builder()
                    .roleId(UUID.randomUUID())
                    .merchantId(merchantId)
                    .roleName(builtIn.name())
                    .description(builtIn.description())
                    .builtin(true)
                    .active(true)
                    .permissions(new ArrayList<>(builtIn.defaultPermissions()))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            roles.add(role);
            seeded.add(role);
        }
        return seeded;
    }

    /**
     * Creates a custom (non-builtin) role for this merchant.
     */
    public Role createCustomRole(String roleName, String description,
                                 List<Permission> permissions, UUID createdBy) {
        var nameExists = roles.stream()
                .filter(Role::active)
                .anyMatch(r -> r.roleName().equalsIgnoreCase(roleName));
        if (nameExists) {
            throw new IllegalArgumentException("Role name already exists: " + roleName);
        }

        var now = Instant.now();
        var role = Role.builder()
                .roleId(UUID.randomUUID())
                .merchantId(merchantId)
                .roleName(roleName)
                .description(description)
                .builtin(false)
                .active(true)
                .permissions(List.copyOf(permissions))
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .build();

        roles.add(role);
        return role;
    }

    /**
     * Deletes (deactivates) a custom role. Built-in roles cannot be deleted.
     * Roles with active users assigned cannot be deleted.
     */
    public Role deleteCustomRole(UUID roleId) {
        var roleIdx = findRoleIndex(roleId);
        var role = roles.get(roleIdx);

        if (role.builtin()) {
            throw BuiltInRoleModificationException.forRole(roleId, role.roleName());
        }

        var activeUsersWithRole = users.stream()
                .filter(u -> u.roleId().equals(roleId)
                        && u.status() != UserStatus.DEACTIVATED)
                .count();
        if (activeUsersWithRole > 0) {
            throw RoleInUseException.forRole(roleId, activeUsersWithRole);
        }

        var deactivated = role.deactivate();
        roles.set(roleIdx, deactivated);
        return deactivated;
    }

    /**
     * Updates permissions on a custom role. Built-in roles cannot be modified.
     */
    public Role updateCustomRolePermissions(UUID roleId, List<Permission> newPermissions) {
        var roleIdx = findRoleIndex(roleId);
        var role = roles.get(roleIdx);

        if (role.builtin()) {
            throw BuiltInRoleModificationException.forRole(roleId, role.roleName());
        }

        var updated = role.updatePermissions(newPermissions);
        roles.set(roleIdx, updated);
        return updated;
    }

    /**
     * Creates the first ADMIN user when a merchant is activated.
     * The user starts in INVITED status — they set their password via the invitation link.
     */
    public MerchantUser createFirstAdmin(String email, String emailHash, String fullName,
                                         String passwordHash) {
        var adminRole = findRoleByName(BuiltInRole.ADMIN.name());

        var user = MerchantUser.builder()
                .userId(UUID.randomUUID())
                .merchantId(merchantId)
                .email(email)
                .emailHash(emailHash)
                .fullName(fullName)
                .status(UserStatus.INVITED)
                .roleId(adminRole.roleId())
                .passwordHash(passwordHash)
                .authProvider(AuthProvider.LOCAL)
                .mfaEnabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        users.add(user);

        return user;
    }

    /**
     * Invites a new user to the merchant team.
     * Creates a user in INVITED status and a pending invitation.
     */
    public InviteResult inviteUser(String email, String emailHash, String fullName,
                                   UUID roleId, UUID invitedBy, String tokenHash) {
        // Invariant: email unique within merchant (among non-deactivated users)
        var emailExists = users.stream()
                .anyMatch(u -> u.emailHash().equals(emailHash)
                        && u.status() != UserStatus.DEACTIVATED);
        if (emailExists) {
            throw UserAlreadyExistsException.forMerchant(merchantId, email);
        }

        // Verify role exists
        var role = findRoleById(roleId);

        var now = Instant.now();

        var user = MerchantUser.builder()
                .userId(UUID.randomUUID())
                .merchantId(merchantId)
                .email(email)
                .emailHash(emailHash)
                .fullName(fullName)
                .status(UserStatus.INVITED)
                .roleId(roleId)
                .authProvider(AuthProvider.LOCAL)
                .mfaEnabled(false)
                .invitedBy(invitedBy)
                .createdAt(now)
                .updatedAt(now)
                .build();

        var invitation = Invitation.builder()
                .invitationId(UUID.randomUUID())
                .merchantId(merchantId)
                .email(email)
                .emailHash(emailHash)
                .roleId(roleId)
                .invitedBy(invitedBy)
                .tokenHash(tokenHash)
                .status(InvitationStatus.PENDING)
                .createdAt(now)
                .expiresAt(now.plus(INVITATION_TTL))
                .build();

        users.add(user);
        invitations.add(invitation);

        domainEvents.add(MerchantUserInvitedEvent.builder()
                .schemaVersion(MerchantUserInvitedEvent.SCHEMA_VERSION)
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserInvitedEvent.EVENT_TYPE)
                .invitationId(invitation.invitationId())
                .userId(user.userId())
                .merchantId(merchantId)
                .emailHash(emailHash)
                .roleId(roleId)
                .roleName(role.roleName())
                .invitedBy(invitedBy)
                .occurredAt(now)
                .build());

        return new InviteResult(user, invitation);
    }

    /**
     * Accepts a pending invitation. Transitions user INVITED → ACTIVE.
     */
    public MerchantUser acceptInvitation(UUID invitationId, String fullName, String passwordHash) {
        var invIdx = findInvitationIndex(invitationId);
        var invitation = invitations.get(invIdx);

        if (invitation.isExpired()) {
            invitations.set(invIdx, invitation.expire());
            throw InvitationExpiredException.withId(invitationId);
        }

        var accepted = invitation.accept();
        if (accepted.status() == InvitationStatus.EXPIRED) {
            invitations.set(invIdx, accepted);
            throw InvitationExpiredException.withId(invitationId);
        }
        invitations.set(invIdx, accepted);

        // Find the corresponding INVITED user by emailHash
        var userIdx = findUserIndexByEmailHash(invitation.emailHash(), UserStatus.INVITED);
        var user = users.get(userIdx);
        var activated = user.acceptInvitation(fullName, passwordHash);
        users.set(userIdx, activated);

        var role = findRoleById(activated.roleId());

        domainEvents.add(MerchantUserActivatedEvent.builder()
                .schemaVersion(MerchantUserActivatedEvent.SCHEMA_VERSION)
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserActivatedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(activated.userId())
                .emailHash(activated.emailHash())
                .roleId(activated.roleId())
                .roleName(role.roleName())
                .occurredAt(Instant.now())
                .build());

        return activated;
    }

    /**
     * Changes a user's role. Enforces last-admin invariant.
     */
    public MerchantUser changeUserRole(UUID userId, UUID newRoleId, UUID changedBy) {
        var userIdx = findUserIndex(userId);
        var user = users.get(userIdx);
        var newRole = findRoleById(newRoleId);
        var previousRoleId = user.roleId();
        var previousRole = findRoleById(previousRoleId);

        // Invariant: cannot demote the last admin
        var adminRole = findRoleByName(BuiltInRole.ADMIN.name());
        if (user.isAdmin(adminRole.roleId()) && !newRoleId.equals(adminRole.roleId())) {
            var adminCount = countActiveAdmins(adminRole.roleId());
            if (adminCount <= 1) {
                throw LastAdminException.forMerchant(merchantId);
            }
        }

        var changed = user.changeRole(newRole.roleId());
        users.set(userIdx, changed);

        domainEvents.add(MerchantUserRoleChangedEvent.builder()
                .schemaVersion(MerchantUserRoleChangedEvent.SCHEMA_VERSION)
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserRoleChangedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(userId)
                .previousRoleId(previousRoleId)
                .previousRoleName(previousRole.roleName())
                .newRoleId(newRoleId)
                .newRoleName(newRole.roleName())
                .changedBy(changedBy)
                .occurredAt(Instant.now())
                .build());

        return changed;
    }

    /**
     * Suspends a user. Transitions ACTIVE → SUSPENDED.
     */
    public MerchantUser suspendUser(UUID userId, String reason, UUID suspendedBy) {
        var userIdx = findUserIndex(userId);
        var user = users.get(userIdx);

        // Invariant: cannot suspend the last admin
        var adminRole = findRoleByName(BuiltInRole.ADMIN.name());
        if (user.isAdmin(adminRole.roleId())) {
            var adminCount = countActiveAdmins(adminRole.roleId());
            if (adminCount <= 1) {
                throw LastAdminException.forMerchant(merchantId);
            }
        }

        var suspended = user.suspend();
        users.set(userIdx, suspended);

        domainEvents.add(MerchantUserSuspendedEvent.builder()
                .schemaVersion(MerchantUserSuspendedEvent.SCHEMA_VERSION)
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserSuspendedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(userId)
                .reason(reason)
                .suspendedBy(suspendedBy)
                .occurredAt(Instant.now())
                .build());

        return suspended;
    }

    /**
     * Reactivates a suspended user. Transitions SUSPENDED → ACTIVE.
     */
    public MerchantUser reactivateUser(UUID userId) {
        var userIdx = findUserIndex(userId);
        var user = users.get(userIdx);
        var reactivated = user.reactivate();
        users.set(userIdx, reactivated);

        var role = findRoleById(reactivated.roleId());

        domainEvents.add(MerchantUserActivatedEvent.builder()
                .schemaVersion(MerchantUserActivatedEvent.SCHEMA_VERSION)
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserActivatedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(reactivated.userId())
                .emailHash(reactivated.emailHash())
                .roleId(reactivated.roleId())
                .roleName(role.roleName())
                .occurredAt(Instant.now())
                .build());

        return reactivated;
    }

    /**
     * Deactivates a user (terminal state). Enforces last-admin invariant.
     */
    public MerchantUser deactivateUser(UUID userId, String reason, UUID deactivatedBy) {
        var userIdx = findUserIndex(userId);
        var user = users.get(userIdx);

        // Invariant: cannot deactivate the last admin
        var adminRole = findRoleByName(BuiltInRole.ADMIN.name());
        if (user.isAdmin(adminRole.roleId()) && user.isActive()) {
            var adminCount = countActiveAdmins(adminRole.roleId());
            if (adminCount <= 1) {
                throw LastAdminException.forMerchant(merchantId);
            }
        }

        var deactivated = user.deactivate();
        users.set(userIdx, deactivated);

        domainEvents.add(MerchantUserDeactivatedEvent.builder()
                .schemaVersion(MerchantUserDeactivatedEvent.SCHEMA_VERSION)
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserDeactivatedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(userId)
                .reason(reason)
                .deactivatedBy(deactivatedBy)
                .occurredAt(Instant.now())
                .build());

        return deactivated;
    }

    /**
     * Revokes all sessions for this merchant (e.g., merchant suspended).
     */
    public AllSessionsRevokedEvent revokeAllSessions(String reason) {
        var event = AllSessionsRevokedEvent.builder()
                .schemaVersion(AllSessionsRevokedEvent.SCHEMA_VERSION)
                .eventId(UUID.randomUUID().toString())
                .eventType(AllSessionsRevokedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .reason(reason)
                .occurredAt(Instant.now())
                .build();

        domainEvents.add(event);
        return event;
    }

    // ── Domain Events ───────────────────────────────────────

    public List<Object> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // ── Internal helpers ────────────────────────────────────

    private int findUserIndex(UUID userId) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).userId().equals(userId)) {
                return i;
            }
        }
        throw UserNotFoundException.withId(userId);
    }

    private int findUserIndexByEmailHash(String emailHash, UserStatus requiredStatus) {
        for (int i = 0; i < users.size(); i++) {
            var u = users.get(i);
            if (u.emailHash().equals(emailHash) && u.status() == requiredStatus) {
                return i;
            }
        }
        throw UserNotFoundException.withEmailHash(merchantId, emailHash);
    }

    private int findInvitationIndex(UUID invitationId) {
        for (int i = 0; i < invitations.size(); i++) {
            if (invitations.get(i).invitationId().equals(invitationId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invitation not in aggregate: " + invitationId);
    }

    private int findRoleIndex(UUID roleId) {
        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i).roleId().equals(roleId)) {
                return i;
            }
        }
        throw RoleNotFoundException.withId(roleId);
    }

    private Role findRoleById(UUID roleId) {
        return roles.stream()
                .filter(r -> r.roleId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> RoleNotFoundException.withId(roleId));
    }

    private Role findRoleByName(String roleName) {
        return roles.stream()
                .filter(r -> r.roleName().equals(roleName))
                .findFirst()
                .orElseThrow(() -> RoleNotFoundException.withName(merchantId, roleName));
    }

    private long countActiveAdmins(UUID adminRoleId) {
        return users.stream()
                .filter(u -> u.roleId().equals(adminRoleId)
                        && u.status() == UserStatus.ACTIVE)
                .count();
    }
}
