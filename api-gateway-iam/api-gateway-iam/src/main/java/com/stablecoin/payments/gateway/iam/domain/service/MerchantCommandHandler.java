package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.event.OAuthClientProvisionedEvent;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.model.Corridor;
import com.stablecoin.payments.gateway.iam.domain.model.KybStatus;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import com.stablecoin.payments.gateway.iam.domain.port.EventPublisher;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.OAuthClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MerchantCommandHandler {

    private final MerchantRepository merchantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final OAuthClientRepository oauthClientRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final OAuthClientCommandHandler oauthClientCommandHandler;
    private final EventPublisher<Object> eventPublisher;

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

    @Transactional(readOnly = true)
    public Merchant findById(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> MerchantNotFoundException.byId(merchantId));
    }

    @Transactional(readOnly = true)
    public Merchant findByExternalId(UUID externalId) {
        return merchantRepository.findByExternalId(externalId)
                .orElseThrow(() -> MerchantNotFoundException.byExternalId(externalId));
    }

    public void activateAndProvisionOAuthClient(UUID externalId, String companyName,
                                                 String country, List<String> scopes) {
        var existing = merchantRepository.findByExternalId(externalId);
        if (existing.isEmpty()) {
            register(externalId, companyName, country, scopes, List.of());
            log.info("Auto-registered merchant from activated event externalId={}", externalId);
        }
        var merchant = activate(externalId);

        var effectiveScopes = (scopes != null && !scopes.isEmpty())
                ? scopes : merchant.getScopes();

        var result = oauthClientCommandHandler.create(
                merchant.getMerchantId(),
                companyName + " Default Client",
                effectiveScopes,
                List.of("client_credentials"));

        var client = result.client();
        eventPublisher.publish(new OAuthClientProvisionedEvent(
                client.getClientId(),
                client.getMerchantId(),
                result.rawSecret(),
                client.getName(),
                client.getScopes(),
                client.getGrantTypes(),
                client.getCreatedAt()));

        log.info("Activated merchant and provisioned default OAuth client externalId={} clientId={}",
                externalId, client.getClientId());
    }
}
