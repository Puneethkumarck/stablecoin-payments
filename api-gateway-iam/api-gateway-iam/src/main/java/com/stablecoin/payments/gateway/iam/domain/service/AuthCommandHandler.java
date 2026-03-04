package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.exception.InvalidClientCredentialsException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ScopeExceededException;
import com.stablecoin.payments.gateway.iam.domain.exception.TokenRevokedException;
import com.stablecoin.payments.gateway.iam.domain.model.AccessToken;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretHasher;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.OAuthClientRepository;
import com.stablecoin.payments.gateway.iam.domain.port.TokenIssuer;
import com.stablecoin.payments.gateway.iam.domain.port.TokenRevocationCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AuthCommandHandler {

    private final OAuthClientRepository oauthClientRepository;
    private final MerchantRepository merchantRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final TokenIssuer tokenIssuer;
    private final ClientSecretHasher clientSecretHasher;
    private final TokenRevocationCache tokenRevocationCache;
    private final long accessTokenTtlSeconds;

    public AuthCommandHandler(OAuthClientRepository oauthClientRepository,
                              MerchantRepository merchantRepository,
                              AccessTokenRepository accessTokenRepository,
                              TokenIssuer tokenIssuer,
                              ClientSecretHasher clientSecretHasher,
                              TokenRevocationCache tokenRevocationCache,
                              @Value("${api-gateway-iam.jwt.access-token-ttl-seconds:3600}") long accessTokenTtlSeconds) {
        this.oauthClientRepository = oauthClientRepository;
        this.merchantRepository = merchantRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.tokenIssuer = tokenIssuer;
        this.clientSecretHasher = clientSecretHasher;
        this.tokenRevocationCache = tokenRevocationCache;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public TokenResult issueToken(UUID clientId, String clientSecret, List<String> requestedScopes) {
        var client = oauthClientRepository.findActiveById(clientId)
                .orElseThrow(InvalidClientCredentialsException::clientNotFound);

        if (!clientSecretHasher.matches(clientSecret, client.getClientSecretHash())) {
            throw InvalidClientCredentialsException.invalidSecret();
        }

        var merchant = merchantRepository.findById(client.getMerchantId())
                .orElseThrow(() -> MerchantNotFoundException.byId(client.getMerchantId()));

        if (!merchant.isActive()) {
            throw MerchantNotActiveException.of(merchant.getMerchantId());
        }

        var scopes = resolveScopes(requestedScopes, client.getScopes());

        var jwtToken = tokenIssuer.issueToken(merchant.getMerchantId(), clientId, scopes);

        var now = Instant.now();
        var accessToken = AccessToken.builder()
                .jti(UUID.randomUUID())
                .merchantId(merchant.getMerchantId())
                .clientId(clientId)
                .scopes(scopes)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenTtlSeconds))
                .revoked(false)
                .build();
        accessTokenRepository.save(accessToken);

        return new TokenResult(jwtToken, accessToken.getJti(), accessTokenTtlSeconds, scopes);
    }

    public void revokeToken(UUID jti) {
        var token = accessTokenRepository.findByJti(jti)
                .orElseThrow(() -> TokenRevokedException.of(jti));

        if (token.isActive()) {
            token.revoke();
            accessTokenRepository.save(token);
            var ttl = Duration.between(Instant.now(), token.getExpiresAt());
            if (!ttl.isNegative()) {
                tokenRevocationCache.markRevoked(jti, ttl);
            }
        }
    }

    @Transactional(readOnly = true)
    public String jwksJson() {
        return tokenIssuer.jwksJson();
    }

    private List<String> resolveScopes(List<String> requested, List<String> allowed) {
        if (requested == null || requested.isEmpty()) {
            return allowed;
        }
        if (!allowed.containsAll(requested)) {
            throw ScopeExceededException.of(requested, allowed);
        }
        return List.copyOf(requested);
    }

    public record TokenResult(String accessToken, UUID jti, long expiresIn, List<String> scopes) {}
}
