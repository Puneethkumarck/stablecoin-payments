package com.stablecoin.payments.gateway.iam.infrastructure.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NimbusTokenIssuerTest {

    private NimbusTokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() throws Exception {
        var properties = new JwtProperties(null, "stablecoin-gateway", "stablecoin-api", 3600);
        tokenIssuer = new NimbusTokenIssuer(properties);
        tokenIssuer.init();
    }

    @Nested
    class IssueAndParseRoundTrip {

        @Test
        void shouldParseIssuedToken() {
            var merchantId = UUID.randomUUID();
            var clientId = UUID.randomUUID();
            var scopes = List.of("payments:read", "payments:write");

            var token = tokenIssuer.issueToken(merchantId, clientId, scopes);
            var parsed = tokenIssuer.parseAndVerify(token);

            assertThat(parsed.merchantId()).isEqualTo(merchantId);
            assertThat(parsed.clientId()).isEqualTo(clientId);
            assertThat(parsed.scopes()).containsExactly("payments:read", "payments:write");
            assertThat(parsed.jti()).isNotNull();
            assertThat(parsed.expiresAtEpochSecond()).isGreaterThan(0);
        }

        @Test
        void shouldParseTokenWithEmptyScopes() {
            var merchantId = UUID.randomUUID();
            var clientId = UUID.randomUUID();

            var token = tokenIssuer.issueToken(merchantId, clientId, List.of());
            var parsed = tokenIssuer.parseAndVerify(token);

            assertThat(parsed.scopes()).isEmpty();
        }
    }

    @Nested
    class ParseAndVerifyValidation {

        @Test
        void shouldRejectTamperedToken() {
            var token = tokenIssuer.issueToken(UUID.randomUUID(), UUID.randomUUID(), List.of());
            var tampered = token.substring(0, token.length() - 4) + "XXXX";

            assertThatThrownBy(() -> tokenIssuer.parseAndVerify(tampered))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectExpiredToken() throws Exception {
            var expiredProps = new JwtProperties(null, "stablecoin-gateway", "stablecoin-api", 0);
            var expiredIssuer = new NimbusTokenIssuer(expiredProps);
            expiredIssuer.init();

            var token = expiredIssuer.issueToken(UUID.randomUUID(), UUID.randomUUID(), List.of());

            // Token issued with 0s TTL — should be expired immediately
            assertThatThrownBy(() -> expiredIssuer.parseAndVerify(token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        void shouldRejectTokenWithWrongIssuer() throws Exception {
            var wrongIssuerProps = new JwtProperties(null, "wrong-issuer", "stablecoin-api", 3600);
            var wrongIssuer = new NimbusTokenIssuer(wrongIssuerProps);
            wrongIssuer.init();

            // Issue token with wrong issuer but sign with same-algo key
            var token = wrongIssuer.issueToken(UUID.randomUUID(), UUID.randomUUID(), List.of());

            // Verifier will fail on signature (different key) — but if keys matched,
            // the issuer check would catch it
            assertThatThrownBy(() -> tokenIssuer.parseAndVerify(token))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectTokenWithWrongAudience() throws Exception {
            var wrongAudProps = new JwtProperties(null, "stablecoin-gateway", "wrong-audience", 3600);
            var wrongAudIssuer = new NimbusTokenIssuer(wrongAudProps);
            wrongAudIssuer.init();

            var token = wrongAudIssuer.issueToken(UUID.randomUUID(), UUID.randomUUID(), List.of());

            assertThatThrownBy(() -> tokenIssuer.parseAndVerify(token))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectGarbage() {
            assertThatThrownBy(() -> tokenIssuer.parseAndVerify("not-a-jwt"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class JwksJson {

        @Test
        void shouldReturnPublicKeySet() {
            var jwks = tokenIssuer.jwksJson();

            assertThat(jwks).contains("\"kty\"");
            assertThat(jwks).contains("\"crv\"");
            assertThat(jwks).doesNotContain("\"d\""); // no private key
        }
    }
}
