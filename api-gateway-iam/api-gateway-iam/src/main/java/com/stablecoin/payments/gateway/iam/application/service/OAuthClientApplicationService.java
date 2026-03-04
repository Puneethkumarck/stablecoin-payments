package com.stablecoin.payments.gateway.iam.application.service;

import com.stablecoin.payments.gateway.iam.api.request.CreateOAuthClientRequest;
import com.stablecoin.payments.gateway.iam.api.response.OAuthClientResponse;
import com.stablecoin.payments.gateway.iam.domain.event.OAuthClientProvisionedEvent;
import com.stablecoin.payments.gateway.iam.domain.port.EventPublisher;
import com.stablecoin.payments.gateway.iam.domain.service.OAuthClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OAuthClientApplicationService {

    private final OAuthClientService oauthClientService;
    private final EventPublisher<Object> eventPublisher;

    public OAuthClientResponse createOAuthClient(UUID merchantId, CreateOAuthClientRequest request) {
        var result = oauthClientService.create(
                merchantId,
                request.name(),
                request.scopes() != null ? request.scopes() : Collections.emptyList(),
                request.grantTypes() != null ? request.grantTypes() : List.of("client_credentials"));

        var client = result.client();

        eventPublisher.publish(new OAuthClientProvisionedEvent(
                client.getClientId(),
                client.getMerchantId(),
                result.rawSecret(),
                client.getName(),
                client.getScopes(),
                client.getGrantTypes(),
                client.getCreatedAt()));

        return new OAuthClientResponse(
                client.getClientId(),
                result.rawSecret(),
                client.getMerchantId(),
                client.getName(),
                client.getScopes(),
                client.getGrantTypes(),
                client.getCreatedAt());
    }
}
