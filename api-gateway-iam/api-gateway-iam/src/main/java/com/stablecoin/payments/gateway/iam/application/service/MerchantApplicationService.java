package com.stablecoin.payments.gateway.iam.application.service;

import com.stablecoin.payments.gateway.iam.api.request.CreateMerchantRequest;
import com.stablecoin.payments.gateway.iam.api.response.MerchantResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.event.OAuthClientProvisionedEvent;
import com.stablecoin.payments.gateway.iam.domain.model.Corridor;
import com.stablecoin.payments.gateway.iam.domain.port.EventPublisher;
import com.stablecoin.payments.gateway.iam.domain.service.MerchantService;
import com.stablecoin.payments.gateway.iam.domain.service.OAuthClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MerchantApplicationService {

    private final MerchantService merchantService;
    private final OAuthClientService oauthClientService;
    private final EventPublisher<Object> eventPublisher;
    private final GatewayResponseMapper mapper;

    public MerchantResponse createMerchant(CreateMerchantRequest request) {
        List<Corridor> corridors = request.corridors() != null
                ? request.corridors().stream()
                        .map(c -> new Corridor(c.sourceCountry(), c.targetCountry()))
                        .toList()
                : Collections.emptyList();

        var merchant = merchantService.register(
                request.externalId(),
                request.name(),
                request.country(),
                request.scopes() != null ? request.scopes() : Collections.emptyList(),
                corridors);

        return mapper.toMerchantResponse(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchant(UUID merchantId) {
        var merchant = merchantService.findById(merchantId);
        return mapper.toMerchantResponse(merchant);
    }

    public void activateAndProvisionOAuthClient(UUID externalId, String companyName,
                                                 List<String> scopes) {
        var merchant = merchantService.activate(externalId);

        var effectiveScopes = (scopes != null && !scopes.isEmpty())
                ? scopes : merchant.getScopes();

        var result = oauthClientService.create(
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
