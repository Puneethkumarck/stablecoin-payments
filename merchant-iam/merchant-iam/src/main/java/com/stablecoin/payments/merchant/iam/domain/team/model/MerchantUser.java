package com.stablecoin.payments.merchant.iam.domain.team.model;

import com.stablecoin.payments.merchant.iam.domain.statemachine.StateMachine;
import com.stablecoin.payments.merchant.iam.domain.statemachine.StateTransition;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.AuthProvider;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus;
import com.stablecoin.payments.merchant.iam.domain.team.model.core.UserTrigger;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus.ACTIVE;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus.DEACTIVATED;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus.INVITED;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserStatus.SUSPENDED;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserTrigger.ACCEPT_INVITATION;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserTrigger.DEACTIVATE;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserTrigger.REACTIVATE;
import static com.stablecoin.payments.merchant.iam.domain.team.model.core.UserTrigger.SUSPEND;

@Builder(toBuilder = true)
public record MerchantUser(
        UUID userId,
        UUID merchantId,
        String email,
        String emailHash,
        String fullName,
        UserStatus status,
        UUID roleId,
        boolean mfaEnabled,
        String mfaSecretRef,
        Instant lastLoginAt,
        String passwordHash,
        AuthProvider authProvider,
        UUID invitedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant suspendedAt,
        Instant deactivatedAt
) {

    private static final StateMachine<UserStatus, UserTrigger> STATE_MACHINE =
            new StateMachine<>(List.of(
                    new StateTransition<>(INVITED, ACCEPT_INVITATION, ACTIVE),
                    new StateTransition<>(ACTIVE, SUSPEND, SUSPENDED),
                    new StateTransition<>(SUSPENDED, REACTIVATE, ACTIVE),
                    new StateTransition<>(ACTIVE, DEACTIVATE, DEACTIVATED),
                    new StateTransition<>(SUSPENDED, DEACTIVATE, DEACTIVATED)
            ));

    public MerchantUser acceptInvitation(String newFullName, String newPasswordHash) {
        UserStatus newStatus = STATE_MACHINE.transition(status, ACCEPT_INVITATION);
        Instant now = Instant.now();
        return toBuilder()
                .status(newStatus)
                .fullName(newFullName)
                .passwordHash(newPasswordHash)
                .activatedAt(now)
                .updatedAt(now)
                .build();
    }

    public MerchantUser suspend() {
        UserStatus newStatus = STATE_MACHINE.transition(status, SUSPEND);
        Instant now = Instant.now();
        return toBuilder()
                .status(newStatus)
                .suspendedAt(now)
                .updatedAt(now)
                .build();
    }

    public MerchantUser reactivate() {
        UserStatus newStatus = STATE_MACHINE.transition(status, REACTIVATE);
        return toBuilder()
                .status(newStatus)
                .suspendedAt(null)
                .updatedAt(Instant.now())
                .build();
    }

    public MerchantUser deactivate() {
        UserStatus newStatus = STATE_MACHINE.transition(status, DEACTIVATE);
        Instant now = Instant.now();
        return toBuilder()
                .status(newStatus)
                .deactivatedAt(now)
                .updatedAt(now)
                .build();
    }

    public MerchantUser changeRole(UUID newRoleId) {
        return toBuilder()
                .roleId(newRoleId)
                .updatedAt(Instant.now())
                .build();
    }

    public MerchantUser recordLogin() {
        Instant now = Instant.now();
        return toBuilder()
                .lastLoginAt(now)
                .updatedAt(now)
                .build();
    }

    public MerchantUser enableMfa(String secretRef) {
        return toBuilder()
                .mfaEnabled(true)
                .mfaSecretRef(secretRef)
                .updatedAt(Instant.now())
                .build();
    }

    public MerchantUser disableMfa() {
        return toBuilder()
                .mfaEnabled(false)
                .mfaSecretRef(null)
                .updatedAt(Instant.now())
                .build();
    }

    public boolean isActive() {
        return status == ACTIVE;
    }

    public boolean isAdmin(UUID adminRoleId) {
        return roleId.equals(adminRoleId);
    }
}
