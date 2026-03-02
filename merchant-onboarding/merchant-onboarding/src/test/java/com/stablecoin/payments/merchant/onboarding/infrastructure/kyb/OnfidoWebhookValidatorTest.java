package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OnfidoWebhookValidator")
class OnfidoWebhookValidatorTest {

  private static final String REAL_SECRET = "my-real-webhook-secret";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  @DisplayName("should accept valid HMAC signature")
  void shouldAcceptValidHmacSignature() throws Exception {
    var validator = new OnfidoWebhookValidator(REAL_SECRET, OBJECT_MAPPER);
    var body = "{\"action\":\"check.completed\"}";
    var signature = computeHmac(REAL_SECRET, body);

    assertThat(validator.isValid(body, signature)).isTrue();
  }

  @Test
  @DisplayName("should reject invalid HMAC signature")
  void shouldRejectInvalidHmacSignature() {
    var validator = new OnfidoWebhookValidator(REAL_SECRET, OBJECT_MAPPER);
    var body = "{\"action\":\"check.completed\"}";

    assertThat(validator.isValid(body, "invalid-signature")).isFalse();
  }

  @Test
  @DisplayName("should reject when signature header is null")
  void shouldRejectWhenSignatureNull() {
    var validator = new OnfidoWebhookValidator(REAL_SECRET, OBJECT_MAPPER);

    assertThat(validator.isValid("{}", null)).isFalse();
  }

  @Test
  @DisplayName("should reject when signature header is blank")
  void shouldRejectWhenSignatureBlank() {
    var validator = new OnfidoWebhookValidator(REAL_SECRET, OBJECT_MAPPER);

    assertThat(validator.isValid("{}", "  ")).isFalse();
  }

  @Test
  @DisplayName("should skip validation when secret is placeholder")
  void shouldSkipValidationWhenSecretIsPlaceholder() {
    var validator = new OnfidoWebhookValidator("sandbox-webhook-secret", OBJECT_MAPPER);

    assertThat(validator.isValid("{}", null)).isTrue();
  }

  @Test
  @DisplayName("should skip validation when secret is blank")
  void shouldSkipValidationWhenSecretIsBlank() {
    var validator = new OnfidoWebhookValidator("", OBJECT_MAPPER);

    assertThat(validator.isValid("{}", null)).isTrue();
  }

  @Test
  @DisplayName("should skip validation when secret is null")
  void shouldSkipValidationWhenSecretIsNull() {
    var validator = new OnfidoWebhookValidator(null, OBJECT_MAPPER);

    assertThat(validator.isValid("{}", null)).isTrue();
  }

  @Test
  @DisplayName("should reject tampered body")
  void shouldRejectTamperedBody() throws Exception {
    var validator = new OnfidoWebhookValidator(REAL_SECRET, OBJECT_MAPPER);
    var originalBody = "{\"action\":\"check.completed\"}";
    var signature = computeHmac(REAL_SECRET, originalBody);
    var tamperedBody = "{\"action\":\"check.completed\",\"injected\":true}";

    assertThat(validator.isValid(tamperedBody, signature)).isFalse();
  }

  @Test
  @DisplayName("should parse valid JSON payload")
  void shouldParseValidJsonPayload() {
    var validator = new OnfidoWebhookValidator(REAL_SECRET, OBJECT_MAPPER);
    var body = "{\"action\":\"check.completed\",\"resource_type\":\"check\"}";

    var result = validator.parsePayload(body);

    assertThat(result).containsEntry("action", "check.completed");
    assertThat(result).containsEntry("resource_type", "check");
  }

  @Test
  @DisplayName("should throw on invalid JSON payload")
  void shouldThrowOnInvalidJsonPayload() {
    var validator = new OnfidoWebhookValidator(REAL_SECRET, OBJECT_MAPPER);

    assertThatThrownBy(() -> validator.parsePayload("not-json")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid webhook payload");
  }

  private static String computeHmac(String secret, String body) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    var keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(keySpec);
    var hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }
}
