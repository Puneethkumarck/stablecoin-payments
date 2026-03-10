package com.stablecoin.payments.offramp.infrastructure.provider.modulr;

import com.stablecoin.payments.offramp.domain.port.WebhookSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Modulr-specific webhook signature validator using HMAC-SHA256.
 * <p>
 * Signature format: {@code t=<timestamp>,v1=<hmac_hex>}
 * where HMAC is computed as {@code HMAC-SHA256(webhook_secret, "<timestamp>.<rawBody>")}.
 * <p>
 * Validates both the HMAC and the timestamp tolerance (default 5 minutes).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payout.provider", havingValue = "modulr")
@EnableConfigurationProperties(ModulrWebhookProperties.class)
public class ModulrWebhookSignatureValidator implements WebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;
    private final Duration tolerance;

    public ModulrWebhookSignatureValidator(ModulrWebhookProperties properties) {
        this.webhookSecret = properties.webhookSecret();
        this.tolerance = Duration.ofSeconds(properties.toleranceSeconds());
    }

    @Override
    public boolean isValid(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            log.warn("[MODULR-WEBHOOK] Missing payload or signature");
            return false;
        }

        try {
            var parsed = parseSignature(signature);
            if (parsed == null) {
                log.warn("[MODULR-WEBHOOK] Failed to parse signature header");
                return false;
            }

            if (!isTimestampValid(parsed.timestamp())) {
                log.warn("[MODULR-WEBHOOK] Timestamp outside tolerance window t={}",
                        parsed.timestamp());
                return false;
            }

            var expectedSignature = computeHmac(parsed.timestamp(), payload);
            var isValid = MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parsed.v1Signature().getBytes(StandardCharsets.UTF_8));

            if (!isValid) {
                log.warn("[MODULR-WEBHOOK] HMAC signature mismatch");
            }
            return isValid;
        } catch (Exception e) {
            log.error("[MODULR-WEBHOOK] Signature validation error", e);
            return false;
        }
    }

    ParsedSignature parseSignature(String header) {
        String timestamp = null;
        String v1Signature = null;

        for (String part : header.split(",")) {
            var kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0].trim()) {
                case "t" -> timestamp = kv[1].trim();
                case "v1" -> v1Signature = kv[1].trim();
                default -> { /* ignore unknown keys */ }
            }
        }

        if (timestamp == null || v1Signature == null) {
            return null;
        }
        return new ParsedSignature(timestamp, v1Signature);
    }

    private boolean isTimestampValid(String timestampStr) {
        try {
            var webhookTime = Instant.ofEpochSecond(Long.parseLong(timestampStr));
            var age = Duration.between(webhookTime, Instant.now()).abs();
            return age.compareTo(tolerance) <= 0;
        } catch (NumberFormatException e) {
            log.warn("[MODULR-WEBHOOK] Invalid timestamp format: {}", timestampStr);
            return false;
        }
    }

    String computeHmac(String timestamp, String payload) {
        try {
            var mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            var signedPayload = timestamp + "." + payload;
            var hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    record ParsedSignature(String timestamp, String v1Signature) {}
}
