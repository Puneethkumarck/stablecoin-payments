package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.CreateOAuthClientRequest;
import com.stablecoin.payments.gateway.iam.api.response.OAuthClientResponse;
import com.stablecoin.payments.gateway.iam.api.response.OAuthClientSummaryResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayRequestResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.service.OAuthClientCommandHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/merchants/{merchantId}/oauth-clients")
@RequiredArgsConstructor
public class OAuthClientController {

    private final OAuthClientCommandHandler oauthClientCommandHandler;
    private final GatewayRequestResponseMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthClientResponse createOAuthClient(
            @PathVariable UUID merchantId,
            @Valid @RequestBody CreateOAuthClientRequest request) {
        log.info("Create OAuth client merchantId={} name={}", merchantId, request.name());

        var result = oauthClientCommandHandler.create(
                merchantId,
                request.name(),
                request.scopes() != null ? request.scopes() : Collections.emptyList(),
                request.grantTypes() != null ? request.grantTypes() : List.of("client_credentials"));

        return mapper.toOAuthClientResponse(result);
    }

    @GetMapping
    public List<OAuthClientSummaryResponse> listOAuthClients(@PathVariable UUID merchantId) {
        log.info("List OAuth clients merchantId={}", merchantId);
        return oauthClientCommandHandler.listByMerchantId(merchantId).stream()
                .map(mapper::toOAuthClientSummaryResponse)
                .toList();
    }
}
