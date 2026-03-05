package com.stablecoin.payments.gateway.iam.infrastructure.config;

import com.stablecoin.payments.gateway.iam.domain.event.ApiKeyRevokedEvent;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyGenerator;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyHasher;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretGenerator;
import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretHasher;
import com.stablecoin.payments.gateway.iam.domain.port.EventPublisher;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.OAuthClientRepository;
import com.stablecoin.payments.gateway.iam.domain.port.TokenIssuer;
import com.stablecoin.payments.gateway.iam.domain.port.TokenRevocationCache;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyService;
import com.stablecoin.payments.gateway.iam.domain.service.AuthService;
import com.stablecoin.payments.gateway.iam.domain.service.MerchantService;
import com.stablecoin.payments.gateway.iam.domain.service.OAuthClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public MerchantService merchantService(MerchantRepository merchantRepository,
                                           ApiKeyRepository apiKeyRepository,
                                           OAuthClientRepository oauthClientRepository,
                                           AccessTokenRepository accessTokenRepository) {
        return new MerchantService(merchantRepository, apiKeyRepository,
                oauthClientRepository, accessTokenRepository);
    }

    @Bean
    public AuthService authService(OAuthClientRepository oauthClientRepository,
                                   MerchantRepository merchantRepository,
                                   AccessTokenRepository accessTokenRepository,
                                   TokenIssuer tokenIssuer,
                                   ClientSecretHasher clientSecretHasher,
                                   TokenRevocationCache tokenRevocationCache,
                                   @Value("${api-gateway-iam.jwt.access-token-ttl-seconds:3600}") long ttl) {
        return new AuthService(oauthClientRepository, merchantRepository, accessTokenRepository,
                tokenIssuer, clientSecretHasher, tokenRevocationCache, ttl);
    }

    @SuppressWarnings("unchecked")
    @Bean
    public ApiKeyService apiKeyService(ApiKeyRepository apiKeyRepository,
                                       MerchantRepository merchantRepository,
                                       ApiKeyGenerator apiKeyGenerator,
                                       ApiKeyHasher apiKeyHasher,
                                       EventPublisher<?> eventPublisher) {
        return new ApiKeyService(apiKeyRepository, merchantRepository,
                apiKeyGenerator, apiKeyHasher, (EventPublisher<ApiKeyRevokedEvent>) eventPublisher);
    }

    @Bean
    public OAuthClientService oauthClientService(OAuthClientRepository oauthClientRepository,
                                                  MerchantRepository merchantRepository,
                                                  ClientSecretGenerator clientSecretGenerator,
                                                  ClientSecretHasher clientSecretHasher) {
        return new OAuthClientService(oauthClientRepository, merchantRepository,
                clientSecretGenerator, clientSecretHasher);
    }
}
