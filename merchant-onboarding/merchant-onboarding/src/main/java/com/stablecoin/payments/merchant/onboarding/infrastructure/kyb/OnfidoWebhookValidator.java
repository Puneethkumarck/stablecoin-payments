package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validates Onfido webhook HMAC-SHA256 signatures.
 * <p>
 * Onfido sends the signature in the {@code X-SHA2-Signature} header. The signature is computed as
 * HMAC-SHA256(webhook_secret, raw_body).
 * <p>
 * In sandbox mode with a placeholder secret, validation is skipped.
 */
@Slf4j
@Component
public class OnfidoWebhookValidator {

  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final String PLACEHOLDER_SECRET = "sandbox-webhook-secret";

  private final String webhookSecret;
  private final ObjectMapper objectMapper;

  public OnfidoWebhookValidator(@Value("${onfido.webhook-secret:}") String webhookSecret, ObjectMapper objectMapper) {
    this.webhookSecret = webhookSecret;
    this.objectMapper = objectMapper;
  }

  public boolean isValid(String rawBody, String signature) {
    if (webhookSecret == null || webhookSecret.isBlank() || PLACEHOLDER_SECRET.equals(webhookSecret)) {
      log.debug("[WEBHOOK-HMAC] Skipping signature validation (sandbox/dev mode)");
      return true;
    }

    if (signature == null || signature.isBlank()) {
      log.warn("[WEBHOOK-HMAC] Missing X-SHA2-Signature header");
      return false;
    }

    try {
      var mac = Mac.getInstance(HMAC_SHA256);
      var keySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
      mac.init(keySpec);
      var expectedBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
      var expectedHex = HexFormat.of().formatHex(expectedBytes);

      return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
          signature.getBytes(StandardCharsets.UTF_8));

    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      log.error("[WEBHOOK-HMAC] HMAC computation failed", ex);
      return false;
    }
  }

  public Map<String, Object> parsePayload(String rawBody) {
    try {
      return objectMapper.readValue(rawBody, new TypeReference<>() {
      });
    } catch (Exception ex) {
      log.error("[WEBHOOK] Failed to parse webhook payload", ex);
      throw new IllegalArgumentException("Invalid webhook payload", ex);
    }
  }
}
