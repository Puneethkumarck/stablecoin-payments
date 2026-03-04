package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ScopeExceededException;
import com.stablecoin.payments.gateway.iam.domain.model.OAuthClient;
import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretGenerator;
import com.stablecoin.payments.gateway.iam.domain.port.ClientSecretHasher;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.OAuthClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OAuthClientCommandHandler {

    private final OAuthClientRepository oauthClientRepository;
    private final MerchantRepository merchantRepository;
    private final ClientSecretGenerator clientSecretGenerator;
    private final ClientSecretHasher clientSecretHasher;

    public CreateOAuthClientResult create(UUID merchantId, String name,
                                          List<String> scopes, List<String> grantTypes) {
        var merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> MerchantNotFoundException.byId(merchantId));

        if (!merchant.isActive()) {
            throw MerchantNotActiveException.of(merchantId);
        }

        if (scopes != null && !scopes.isEmpty() && !merchant.getScopes().containsAll(scopes)) {
            throw ScopeExceededException.of(scopes, merchant.getScopes());
        }

        var rawSecret = clientSecretGenerator.generate();
        var hash = clientSecretHasher.hash(rawSecret);

        var effectiveScopes = (scopes != null && !scopes.isEmpty())
                ? List.copyOf(scopes) : List.copyOf(merchant.getScopes());
        var effectiveGrantTypes = (grantTypes != null && !grantTypes.isEmpty())
                ? List.copyOf(grantTypes) : List.of("client_credentials");

        var client = OAuthClient.builder()
                .clientId(UUID.randomUUID())
                .merchantId(merchantId)
                .clientSecretHash(hash)
                .name(name)
                .scopes(effectiveScopes)
                .grantTypes(effectiveGrantTypes)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        var saved = oauthClientRepository.save(client);
        return new CreateOAuthClientResult(saved, rawSecret);
    }

    public record CreateOAuthClientResult(OAuthClient client, String rawSecret) {}
}
