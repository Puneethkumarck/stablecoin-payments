package com.stablecoin.payments.custody.infrastructure.provider.fireblocks;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.port.CustodyEngine;
import com.stablecoin.payments.custody.domain.port.SignRequest;
import com.stablecoin.payments.custody.domain.port.SignResult;
import com.stablecoin.payments.custody.domain.port.TransactionStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.custody.provider", havingValue = "fireblocks")
@EnableConfigurationProperties(FireblocksProperties.class)
public class FireblocksCustodyAdapter implements CustodyEngine {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static final Map<String, String> ASSET_ID_MAP = Map.of(
            "base:USDC", "USDC_BASE",
            "ethereum:USDC", "USDC",
            "solana:USDC", "USDC_SOL"
    );

    private final RestClient restClient;
    private final FireblocksProperties properties;
    private final RSAPrivateKey privateKey;

    public FireblocksCustodyAdapter(FireblocksProperties properties) {
        this.properties = properties;

        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();

        this.privateKey = parsePrivateKey(properties.apiSecret());
    }

    @Override
    @CircuitBreaker(name = "fireblocks", fallbackMethod = "signAndSubmitFallback")
    public SignResult signAndSubmit(SignRequest request) {
        log.info("[FIREBLOCKS] Signing and submitting transfer transferId={} chain={} to={}",
                request.transferId(), request.chainId().value(), request.toAddress());

        var assetId = resolveAssetId(request.chainId(), request.stablecoin());
        var vaultId = request.vaultAccountId() != null
                ? request.vaultAccountId()
                : properties.vaultAccountId();

        var body = new FireblocksCreateTransactionRequest(
                assetId,
                new FireblocksCreateTransactionRequest.TransferPeerPath("VAULT_ACCOUNT", vaultId),
                new FireblocksCreateTransactionRequest.DestinationTransferPeerPath(
                        "ONE_TIME_ADDRESS",
                        new FireblocksCreateTransactionRequest.OneTimeAddress(request.toAddress())
                ),
                request.amount().toPlainString(),
                "transfer_" + request.transferId(),
                request.transferId().toString()
        );

        var uri = "/v1/transactions";
        var jwt = buildJwt(uri, body);

        var response = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .header("X-API-Key", properties.apiKey())
                .body(body)
                .retrieve()
                .body(FireblocksCreateTransactionResponse.class);

        log.info("[FIREBLOCKS] Transaction created transferId={} custodyTxId={} status={}",
                request.transferId(), response.id(), response.status());

        return new SignResult(null, response.id());
    }

    @Override
    @CircuitBreaker(name = "fireblocks", fallbackMethod = "getTransactionStatusFallback")
    public TransactionStatus getTransactionStatus(String txId) {
        log.info("[FIREBLOCKS] Getting transaction status txId={}", txId);

        var uri = "/v1/transactions/" + txId;
        var jwt = buildJwt(uri, null);

        var response = restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + jwt)
                .header("X-API-Key", properties.apiKey())
                .retrieve()
                .body(FireblocksTransactionStatusResponse.class);

        log.info("[FIREBLOCKS] Transaction status txId={} status={} confirmations={}",
                txId, response.status(), response.numOfConfirmations());

        return new TransactionStatus(response.status(), response.txHash(), response.numOfConfirmations());
    }

    @SuppressWarnings("unused")
    private SignResult signAndSubmitFallback(SignRequest request, Exception ex) {
        log.error("[FIREBLOCKS] Circuit breaker open — signAndSubmit failed transferId={}",
                request.transferId(), ex);
        throw new IllegalStateException("Fireblocks custody unavailable", ex);
    }

    @SuppressWarnings("unused")
    private TransactionStatus getTransactionStatusFallback(String txId, Exception ex) {
        log.error("[FIREBLOCKS] Circuit breaker open — getTransactionStatus failed txId={}", txId, ex);
        throw new IllegalStateException("Fireblocks custody unavailable", ex);
    }

    String resolveAssetId(ChainId chainId, StablecoinTicker stablecoin) {
        var key = chainId.value() + ":" + stablecoin.ticker();
        var assetId = ASSET_ID_MAP.get(key);
        if (assetId == null) {
            throw new IllegalArgumentException(
                    "Unsupported chain/stablecoin combination: %s".formatted(key));
        }
        return assetId;
    }

    private RSAPrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            log.warn("[FIREBLOCKS] No API secret configured — JWT signing will fail at runtime");
            return null;
        }
        try {
            var stripped = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            var decoded = Base64.getDecoder().decode(stripped);
            var keySpec = new PKCS8EncodedKeySpec(decoded);
            var keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Fireblocks RSA private key", ex);
        }
    }

    private String buildJwt(String uri, Object body) {
        try {
            var now = Instant.now();
            var headerJson = """
                    {"alg":"RS256","typ":"JWT"}""";
            var bodyHash = sha256Hex(body != null ? serializeBody(body) : "");

            var payloadJson = """
                    {"sub":"%s","nonce":"%s","iat":%d,"exp":%d,"uri":"%s","bodyHash":"%s"}"""
                    .formatted(
                            properties.apiKey(),
                            UUID.randomUUID().toString(),
                            now.getEpochSecond(),
                            now.getEpochSecond() + 30,
                            uri,
                            bodyHash
                    );

            var encoder = Base64.getUrlEncoder().withoutPadding();
            var headerB64 = encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            var payloadB64 = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            var signingInput = headerB64 + "." + payloadB64;
            var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            var signatureB64 = encoder.encodeToString(signature.sign());

            return signingInput + "." + signatureB64;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build Fireblocks JWT", ex);
        }
    }

    private String serializeBody(Object body) {
        try {
            return JSON_MAPPER.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize request body", ex);
        }
    }

    private String sha256Hex(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            var hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute SHA-256 hash", ex);
        }
    }
}
