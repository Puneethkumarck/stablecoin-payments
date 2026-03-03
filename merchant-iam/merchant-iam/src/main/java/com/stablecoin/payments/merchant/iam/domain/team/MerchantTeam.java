package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.exceptions.InvitationExpiredException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.LastAdminException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.RoleNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserAlreadyExistsException;
import com.stablecoin.payments.merchant.iam.domain.exceptions.UserNotFoundException;
import com.stablecoin.payments.merchant.iam.domain.team.model.Invitation;
import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;
import com.stablecoin.payments.merchant.iam.domain.team.model.Role;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.AuthProvider;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.BuiltInRole;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.InvitationStatus;
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
        List<Role> seeded = new ArrayList<>();
        for (BuiltInRole builtIn : BuiltInRole.values()) {
            Role role = Role.builder()
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
     * Creates the first ADMIN user when a merchant is activated.
     * The user starts in ACTIVE status (no invitation needed).
     */
    public MerchantUser createFirstAdmin(String email, String emailHash, String fullName,
                                         String passwordHash) {
        Role adminRole = findRoleByName(BuiltInRole.ADMIN.name());

        MerchantUser user = MerchantUser.builder()
                .userId(UUID.randomUUID())
                .merchantId(merchantId)
                .email(email)
                .emailHash(emailHash)
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .roleId(adminRole.roleId())
                .passwordHash(passwordHash)
                .authProvider(AuthProvider.LOCAL)
                .mfaEnabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .activatedAt(Instant.now())
                .build();

        users.add(user);

        domainEvents.add(MerchantUserActivatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserActivatedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(user.userId())
                .email(email)
                .roleId(adminRole.roleId())
                .occurredAt(Instant.now())
                .build());

        return user;
    }

    /**
     * Invites a new user to the merchant team.
     * Creates a user in INVITED status and a pending invitation.
     */
    public InviteResult inviteUser(String email, String emailHash, String fullName,
                                   UUID roleId, UUID invitedBy, String tokenHash) {
        // Invariant: email unique within merchant (among non-deactivated users)
        boolean emailExists = users.stream()
                .anyMatch(u -> u.emailHash().equals(emailHash)
                        && u.status() != UserStatus.DEACTIVATED);
        if (emailExists) {
            throw new UserAlreadyExistsException(merchantId, email);
        }

        // Verify role exists
        findRoleById(roleId);

        Instant now = Instant.now();

        MerchantUser user = MerchantUser.builder()
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

        Invitation invitation = Invitation.builder()
                .invitationId(UUID.randomUUID())
                .merchantId(merchantId)
                .email(email)
                .emailHash(emailHash)
                .fullName(fullName)
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
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserInvitedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(user.userId())
                .email(email)
                .roleId(roleId)
                .invitedBy(invitedBy)
                .occurredAt(now)
                .build());

        return new InviteResult(user, invitation);
    }

    /**
     * Accepts a pending invitation. Transitions user INVITED → ACTIVE.
     */
    public MerchantUser acceptInvitation(UUID invitationId, String fullName, String passwordHash) {
        int invIdx = findInvitationIndex(invitationId);
        Invitation invitation = invitations.get(invIdx);

        if (invitation.isExpired()) {
            invitations.set(invIdx, invitation.expire());
            throw new InvitationExpiredException(invitationId);
        }

        Invitation accepted = invitation.accept();
        if (accepted.status() == InvitationStatus.EXPIRED) {
            invitations.set(invIdx, accepted);
            throw new InvitationExpiredException(invitationId);
        }
        invitations.set(invIdx, accepted);

        // Find the corresponding INVITED user by emailHash
        int userIdx = findUserIndexByEmailHash(invitation.emailHash(), UserStatus.INVITED);
        MerchantUser user = users.get(userIdx);
        MerchantUser activated = user.acceptInvitation(fullName, passwordHash);
        users.set(userIdx, activated);

        domainEvents.add(MerchantUserActivatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserActivatedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(activated.userId())
                .email(activated.email())
                .roleId(activated.roleId())
                .occurredAt(Instant.now())
                .build());

        return activated;
    }

    /**
     * Changes a user's role. Enforces last-admin invariant.
     */
    public MerchantUser changeUserRole(UUID userId, UUID newRoleId, UUID changedBy) {
        int userIdx = findUserIndex(userId);
        MerchantUser user = users.get(userIdx);
        Role newRole = findRoleById(newRoleId);
        UUID previousRoleId = user.roleId();

        // Invariant: cannot demote the last admin
        Role adminRole = findRoleByName(BuiltInRole.ADMIN.name());
        if (user.isAdmin(adminRole.roleId()) && !newRoleId.equals(adminRole.roleId())) {
            long adminCount = countActiveAdmins(adminRole.roleId());
            if (adminCount <= 1) {
                throw new LastAdminException(merchantId);
            }
        }

        MerchantUser changed = user.changeRole(newRole.roleId());
        users.set(userIdx, changed);

        domainEvents.add(MerchantUserRoleChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserRoleChangedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(userId)
                .previousRoleId(previousRoleId)
                .newRoleId(newRoleId)
                .changedBy(changedBy)
                .occurredAt(Instant.now())
                .build());

        return changed;
    }

    /**
     * Suspends a user. Transitions ACTIVE → SUSPENDED.
     */
    public MerchantUser suspendUser(UUID userId, String reason, UUID suspendedBy) {
        int userIdx = findUserIndex(userId);
        MerchantUser user = users.get(userIdx);

        // Invariant: cannot suspend the last admin
        Role adminRole = findRoleByName(BuiltInRole.ADMIN.name());
        if (user.isAdmin(adminRole.roleId())) {
            long adminCount = countActiveAdmins(adminRole.roleId());
            if (adminCount <= 1) {
                throw new LastAdminException(merchantId);
            }
        }

        MerchantUser suspended = user.suspend();
        users.set(userIdx, suspended);

        domainEvents.add(MerchantUserSuspendedEvent.builder()
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
        int userIdx = findUserIndex(userId);
        MerchantUser user = users.get(userIdx);
        MerchantUser reactivated = user.reactivate();
        users.set(userIdx, reactivated);

        domainEvents.add(MerchantUserActivatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantUserActivatedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .userId(reactivated.userId())
                .email(reactivated.email())
                .roleId(reactivated.roleId())
                .occurredAt(Instant.now())
                .build());

        return reactivated;
    }

    /**
     * Deactivates a user (terminal state). Enforces last-admin invariant.
     */
    public MerchantUser deactivateUser(UUID userId, String reason, UUID deactivatedBy) {
        int userIdx = findUserIndex(userId);
        MerchantUser user = users.get(userIdx);

        // Invariant: cannot deactivate the last admin
        Role adminRole = findRoleByName(BuiltInRole.ADMIN.name());
        if (user.isAdmin(adminRole.roleId()) && user.isActive()) {
            long adminCount = countActiveAdmins(adminRole.roleId());
            if (adminCount <= 1) {
                throw new LastAdminException(merchantId);
            }
        }

        MerchantUser deactivated = user.deactivate();
        users.set(userIdx, deactivated);

        domainEvents.add(MerchantUserDeactivatedEvent.builder()
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
        AllSessionsRevokedEvent event = AllSessionsRevokedEvent.builder()
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
        throw new UserNotFoundException(userId);
    }

    private int findUserIndexByEmailHash(String emailHash, UserStatus requiredStatus) {
        for (int i = 0; i < users.size(); i++) {
            MerchantUser u = users.get(i);
            if (u.emailHash().equals(emailHash) && u.status() == requiredStatus) {
                return i;
            }
        }
        throw new UserNotFoundException(merchantId, emailHash);
    }

    private int findInvitationIndex(UUID invitationId) {
        for (int i = 0; i < invitations.size(); i++) {
            if (invitations.get(i).invitationId().equals(invitationId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invitation not in aggregate: " + invitationId);
    }

    private Role findRoleById(UUID roleId) {
        return roles.stream()
                .filter(r -> r.roleId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new RoleNotFoundException(roleId));
    }

    private Role findRoleByName(String roleName) {
        return roles.stream()
                .filter(r -> r.roleName().equals(roleName))
                .findFirst()
                .orElseThrow(() -> new RoleNotFoundException(merchantId, roleName));
    }

    private long countActiveAdmins(UUID adminRoleId) {
        return users.stream()
                .filter(u -> u.roleId().equals(adminRoleId)
                        && u.status() == UserStatus.ACTIVE)
                .count();
    }
}
