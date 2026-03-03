package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stablecoin.payments.gateway.iam.domain.port.TokenIssuer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NimbusTokenIssuer implements TokenIssuer {

    private final JwtProperties properties;

    private ECKey signingKey;
    private ECDSASigner signer;

    @PostConstruct
    void init() throws Exception {
        if (properties.privateKeyBase64() == null || properties.privateKeyBase64().isBlank()) {
            signingKey = new ECKeyGenerator(Curve.P_256)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.ES256)
                    .generate();
            log.warn("No JWT private key configured — using ephemeral key (dev mode only). "
                    + "Set api-gateway-iam.jwt.private-key-base64 for production.");
        } else {
            signingKey = (ECKey) ECKey.parseFromPEMEncodedObjects(
                    "-----BEGIN EC PRIVATE KEY-----\n"
                            + properties.privateKeyBase64() + "\n"
                            + "-----END EC PRIVATE KEY-----");
        }
        signer = new ECDSASigner(signingKey);
        log.info("JWT token issuer initialised keyId={}", signingKey.getKeyID());
    }

    @Override
    public String issueToken(UUID merchantId, UUID clientId, List<String> scopes) {
        try {
            var now = Instant.now();
            var expiry = now.plusSeconds(properties.accessTokenTtlSeconds());

            var claims = new JWTClaimsSet.Builder()
                    .issuer(properties.issuer())
                    .subject(clientId.toString())
                    .audience(properties.audience())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .claim("merchant_id", merchantId.toString())
                    .claim("client_id", clientId.toString())
                    .claim("scope", String.join(" ", scopes))
                    .build();

            var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingKey.getKeyID())
                    .build();

            var jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue access token", e);
        }
    }

    @Override
    public String jwksJson() {
        try {
            var publicKey = signingKey.toPublicJWK();
            return new JWKSet(publicKey).toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JWKS", e);
        }
    }
}
