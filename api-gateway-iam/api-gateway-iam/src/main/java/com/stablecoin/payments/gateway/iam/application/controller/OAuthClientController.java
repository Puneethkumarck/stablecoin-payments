package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.CreateOAuthClientRequest;
import com.stablecoin.payments.gateway.iam.api.response.OAuthClientResponse;
import com.stablecoin.payments.gateway.iam.application.service.OAuthClientApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/merchants/{merchantId}/oauth-clients")
@RequiredArgsConstructor
public class OAuthClientController {

    private final OAuthClientApplicationService oauthClientApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthClientResponse createOAuthClient(
            @PathVariable UUID merchantId,
            @Valid @RequestBody CreateOAuthClientRequest request) {
        log.info("Create OAuth client merchantId={} name={}", merchantId, request.name());
        return oauthClientApplicationService.createOAuthClient(merchantId, request);
    }
}
