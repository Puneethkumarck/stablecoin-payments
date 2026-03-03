package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.AccessToken;

import java.util.Optional;
import java.util.UUID;

public interface AccessTokenRepository {

    AccessToken save(AccessToken token);

    Optional<AccessToken> findByJti(UUID jti);

    void revokeAllByMerchantId(UUID merchantId);

    void deleteExpiredBefore(java.time.Instant cutoff);
}
