package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.model.Corridor;
import com.stablecoin.payments.gateway.iam.domain.model.KybStatus;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.OAuthClientRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final OAuthClientRepository oauthClientRepository;
    private final AccessTokenRepository accessTokenRepository;

    public MerchantService(MerchantRepository merchantRepository,
                           ApiKeyRepository apiKeyRepository,
                           OAuthClientRepository oauthClientRepository,
                           AccessTokenRepository accessTokenRepository) {
        this.merchantRepository = merchantRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.oauthClientRepository = oauthClientRepository;
        this.accessTokenRepository = accessTokenRepository;
    }

    public Merchant register(UUID externalId, String name, String country,
                             List<String> scopes, List<Corridor> corridors) {
        var merchant = Merchant.builder()
                .merchantId(UUID.randomUUID())
                .externalId(externalId)
                .name(name)
                .country(country)
                .scopes(scopes != null ? List.copyOf(scopes) : List.of())
                .corridors(corridors != null ? List.copyOf(corridors) : List.of())
                .status(MerchantStatus.PENDING)
                .kybStatus(KybStatus.PENDING)
                .rateLimitTier(RateLimitTier.STARTER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
        return merchantRepository.save(merchant);
    }

    public Merchant activate(UUID externalId) {
        var merchant = merchantRepository.findByExternalId(externalId)
                .orElseThrow(() -> MerchantNotFoundException.byExternalId(externalId));
        merchant.activate();
        return merchantRepository.save(merchant);
    }

    public Merchant suspend(UUID externalId) {
        var merchant = merchantRepository.findByExternalId(externalId)
                .orElseThrow(() -> MerchantNotFoundException.byExternalId(externalId));
        merchant.suspend();
        apiKeyRepository.deactivateAllByMerchantId(merchant.getMerchantId());
        oauthClientRepository.deactivateAllByMerchantId(merchant.getMerchantId());
        accessTokenRepository.revokeAllByMerchantId(merchant.getMerchantId());
        return merchantRepository.save(merchant);
    }

    public Merchant close(UUID externalId) {
        var merchant = merchantRepository.findByExternalId(externalId)
                .orElseThrow(() -> MerchantNotFoundException.byExternalId(externalId));
        merchant.close();
        apiKeyRepository.deactivateAllByMerchantId(merchant.getMerchantId());
        oauthClientRepository.deactivateAllByMerchantId(merchant.getMerchantId());
        accessTokenRepository.revokeAllByMerchantId(merchant.getMerchantId());
        return merchantRepository.save(merchant);
    }

    public Merchant findById(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> MerchantNotFoundException.byId(merchantId));
    }

    public Merchant findByExternalId(UUID externalId) {
        return merchantRepository.findByExternalId(externalId)
                .orElseThrow(() -> MerchantNotFoundException.byExternalId(externalId));
    }
}
