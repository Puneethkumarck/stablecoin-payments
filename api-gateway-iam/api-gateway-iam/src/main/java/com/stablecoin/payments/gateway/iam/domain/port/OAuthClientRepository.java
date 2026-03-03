package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.OAuthClient;

import java.util.Optional;
import java.util.UUID;

public interface OAuthClientRepository {

    OAuthClient save(OAuthClient client);

    Optional<OAuthClient> findById(UUID clientId);

    Optional<OAuthClient> findActiveById(UUID clientId);

    void deactivateAllByMerchantId(UUID merchantId);
}
