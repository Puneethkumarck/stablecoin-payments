package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.OAuthClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OAuthClientRepository {

    OAuthClient save(OAuthClient client);

    Optional<OAuthClient> findById(UUID clientId);

    Optional<OAuthClient> findActiveById(UUID clientId);

    List<OAuthClient> findByMerchantId(UUID merchantId);

    void deactivateAllByMerchantId(UUID merchantId);
}
