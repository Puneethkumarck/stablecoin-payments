package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantAccessDeniedException;
import com.stablecoin.payments.gateway.iam.domain.exception.TokenRevokedException;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Extracts the authenticated principal's merchant ID from the SecurityContext
 * and enforces that it matches the target merchant ID.
 * Used via {@code @PreAuthorize("@merchantScopeEnforcer.hasAccess(#merchantId)")}.
 */
@Component
@RequiredArgsConstructor
public class MerchantScopeEnforcer {

    private final ApiKeyRepository apiKeyRepository;
    private final AccessTokenRepository accessTokenRepository;

    /**
     * Checks whether the authenticated principal has access to the given merchant.
     *
     * @return true if the principal's merchant ID matches the target
     * @throws MerchantAccessDeniedException if no merchant-scoped authentication is present or IDs don't match
     */
    public boolean hasAccess(UUID targetMerchantId) {
        var principalMerchantId = authenticatedMerchantId();
        if (!principalMerchantId.equals(targetMerchantId)) {
            throw MerchantAccessDeniedException.forMerchant(targetMerchantId);
        }
        return true;
    }

    /**
     * Checks whether the authenticated principal owns the API key.
     *
     * @return true if the key's merchant ID matches the principal's
     * @throws ApiKeyNotFoundException if the key does not exist
     * @throws MerchantAccessDeniedException if the principal does not own the key
     */
    public boolean hasAccessToApiKey(UUID keyId) {
        var apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> ApiKeyNotFoundException.byId(keyId));
        return hasAccess(apiKey.getMerchantId());
    }

    /**
     * Checks whether the authenticated principal owns the access token.
     *
     * @return true if the token's merchant ID matches the principal's
     * @throws TokenRevokedException if the token does not exist
     * @throws MerchantAccessDeniedException if the principal does not own the token
     */
    public boolean hasAccessToToken(UUID jti) {
        var token = accessTokenRepository.findByJti(jti)
                .orElseThrow(() -> TokenRevokedException.of(jti));
        return hasAccess(token.getMerchantId());
    }

    /**
     * Returns the authenticated principal's merchant ID.
     *
     * @throws MerchantAccessDeniedException if no merchant-scoped authentication is present
     */
    public UUID authenticatedMerchantId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return extractMerchantId(auth);
    }

    private UUID extractMerchantId(Authentication auth) {
        if (auth instanceof MerchantAuthentication merchant) {
            return merchant.merchantId();
        }
        if (auth instanceof UserAuthentication user) {
            return user.merchantId();
        }
        throw MerchantAccessDeniedException.forMerchant(null);
    }
}
