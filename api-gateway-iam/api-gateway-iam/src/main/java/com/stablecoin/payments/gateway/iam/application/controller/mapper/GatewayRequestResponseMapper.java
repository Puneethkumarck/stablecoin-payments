package com.stablecoin.payments.gateway.iam.application.controller.mapper;

import com.stablecoin.payments.gateway.iam.api.response.ApiKeyResponse;
import com.stablecoin.payments.gateway.iam.api.response.MerchantResponse;
import com.stablecoin.payments.gateway.iam.api.response.OAuthClientResponse;
import com.stablecoin.payments.gateway.iam.api.response.TokenResponse;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyCommandHandler;
import com.stablecoin.payments.gateway.iam.domain.service.AuthCommandHandler;
import com.stablecoin.payments.gateway.iam.domain.service.OAuthClientCommandHandler;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface GatewayRequestResponseMapper {

    @Mapping(target = "status", expression = "java(merchant.getStatus().name())")
    @Mapping(target = "kybStatus", expression = "java(merchant.getKybStatus().name())")
    @Mapping(target = "rateLimitTier", expression = "java(merchant.getRateLimitTier().name())")
    MerchantResponse toMerchantResponse(Merchant merchant);

    default ApiKeyResponse toApiKeyResponse(ApiKeyCommandHandler.CreateApiKeyResult result) {
        var key = result.apiKey();
        return new ApiKeyResponse(
                key.getKeyId(),
                result.rawKey(),
                key.getKeyPrefix(),
                key.getName(),
                key.getEnvironment().name(),
                key.getScopes(),
                key.getAllowedIps(),
                key.getExpiresAt(),
                key.getCreatedAt());
    }

    default TokenResponse toTokenResponse(AuthCommandHandler.TokenResult result) {
        return new TokenResponse(
                result.accessToken(),
                "Bearer",
                (int) result.expiresIn(),
                String.join(" ", result.scopes()));
    }

    default OAuthClientResponse toOAuthClientResponse(OAuthClientCommandHandler.CreateOAuthClientResult result) {
        var client = result.client();
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
