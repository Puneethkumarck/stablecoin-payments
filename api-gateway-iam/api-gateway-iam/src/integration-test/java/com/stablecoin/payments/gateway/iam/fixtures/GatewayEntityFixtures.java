package com.stablecoin.payments.gateway.iam.fixtures;

import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.AccessTokenEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.ApiKeyEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.AuditLogEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.MerchantEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.OAuthClientEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.RateLimitEventEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class GatewayEntityFixtures {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID DEFAULT_MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEFAULT_EXTERNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private GatewayEntityFixtures() {}

    public static UUID defaultMerchantId() {
        return DEFAULT_MERCHANT_ID;
    }

    public static MerchantEntity anActiveMerchant() {
        return MerchantEntity.builder()
                .merchantId(DEFAULT_MERCHANT_ID)
                .externalId(DEFAULT_EXTERNAL_ID)
                .name("Test Merchant")
                .country("US")
                .scopes(new String[]{"payments:read", "payments:write"})
                .corridors("[]")
                .status("ACTIVE")
                .kybStatus("VERIFIED")
                .rateLimitTier("STARTER")
                .createdAt(BASE_TIME)
                .updatedAt(BASE_TIME)
                .build();
    }

    public static MerchantEntity aPendingMerchant() {
        return MerchantEntity.builder()
                .merchantId(UUID.randomUUID())
                .externalId(UUID.randomUUID())
                .name("Pending Merchant")
                .country("DE")
                .scopes(new String[]{})
                .corridors("[]")
                .status("PENDING")
                .kybStatus("PENDING")
                .rateLimitTier("STARTER")
                .createdAt(BASE_TIME)
                .updatedAt(BASE_TIME)
                .build();
    }

    public static ApiKeyEntity anActiveApiKey() {
        return anActiveApiKey(DEFAULT_MERCHANT_ID);
    }

    public static ApiKeyEntity anActiveApiKey(UUID merchantId) {
        return ApiKeyEntity.builder()
                .keyId(UUID.randomUUID())
                .merchantId(merchantId)
                .keyHash("sha256_" + UUID.randomUUID().toString().replace("-", ""))
                .keyPrefix("pk_live_abc")
                .name("Test API Key")
                .environment("LIVE")
                .scopes(new String[]{"payments:read"})
                .allowedIps(new String[]{"192.168.1.1"})
                .active(true)
                .expiresAt(BASE_TIME.plusSeconds(86400))
                .createdAt(BASE_TIME)
                .updatedAt(BASE_TIME)
                .build();
    }

    public static OAuthClientEntity anActiveOAuthClient() {
        return anActiveOAuthClient(DEFAULT_MERCHANT_ID);
    }

    public static OAuthClientEntity anActiveOAuthClient(UUID merchantId) {
        return OAuthClientEntity.builder()
                .clientId(UUID.randomUUID())
                .merchantId(merchantId)
                .clientSecretHash("$2a$12$fakehash" + UUID.randomUUID().toString().replace("-", "").substring(0, 22))
                .name("Test OAuth Client")
                .scopes(new String[]{"payments:read", "payments:write"})
                .grantTypes(new String[]{"client_credentials"})
                .active(true)
                .createdAt(BASE_TIME)
                .updatedAt(BASE_TIME)
                .build();
    }

    public static AccessTokenEntity anActiveAccessToken(UUID merchantId, UUID clientId) {
        return AccessTokenEntity.builder()
                .jti(UUID.randomUUID())
                .merchantId(merchantId)
                .clientId(clientId)
                .scopes(new String[]{"payments:read"})
                .issuedAt(BASE_TIME)
                .expiresAt(BASE_TIME.plusSeconds(3600))
                .revoked(false)
                .build();
    }

    public static RateLimitEventEntity aRateLimitEvent(UUID merchantId) {
        return RateLimitEventEntity.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(BASE_TIME)
                .merchantId(merchantId)
                .endpoint("/v1/payments")
                .tier("STARTER")
                .requestCount(61)
                .limitValue(60)
                .breached(true)
                .build();
    }

    public static AuditLogEntity anAuditLogEntry(UUID merchantId) {
        return AuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .occurredAt(BASE_TIME)
                .merchantId(merchantId)
                .action("TOKEN_ISSUED")
                .resource("/v1/auth/token")
                .sourceIp("192.168.1.1")
                .detail(Map.of("client_id", UUID.randomUUID().toString()))
                .build();
    }
}
