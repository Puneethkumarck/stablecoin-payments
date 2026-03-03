package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.team.model.UserSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository {

    Optional<UserSession> findById(UUID sessionId);

    List<UserSession> findActiveByUserId(UUID userId);

    UserSession save(UserSession session);

    int revokeAllByUserId(UUID userId, String reason);

    int revokeAllByMerchantId(UUID merchantId, String reason);
}
