package com.stablecoin.payments.merchant.onboarding.application.controller;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.KybProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybVerification;
import com.stablecoin.payments.merchant.onboarding.infrastructure.kyb.OnfidoWebhookValidator;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal.KybResultSignal;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.workflow.MerchantOnboardingWorkflow;
import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Receives KYB provider webhook callbacks (Onfido check.completed events). Validates HMAC signature, delegates to
 * KybProvider.handleWebhook(), then signals the Temporal onboarding workflow with the result.
 * <p>
 * No @PreAuthorize — webhook endpoints are authenticated via HMAC signature.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/webhooks")
@RequiredArgsConstructor
public class KybWebhookController {

  private final KybProvider kybProvider;
  private final WorkflowClient workflowClient;
  private final OnfidoWebhookValidator webhookValidator;

  @PostMapping("/onfido")
  public ResponseEntity<Void> handleOnfidoWebhook(@RequestBody String rawBody,
      @RequestHeader(value = "X-SHA2-Signature", required = false) String signature) {

    if (!webhookValidator.isValid(rawBody, signature)) {
      log.warn("[WEBHOOK] Invalid Onfido webhook signature");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Map<String, Object> payload = webhookValidator.parsePayload(rawBody);
    log.info("[WEBHOOK] Onfido webhook received action={}", payload.get("action"));

    KybVerification result = kybProvider.handleWebhook(payload);
    if (result == null) {
      log.debug("[WEBHOOK] Non-check webhook ignored");
      return ResponseEntity.ok().build();
    }

    UUID merchantId = resolveMerchantId(result, payload);
    if (merchantId == null) {
      log.warn("[WEBHOOK] Could not resolve merchantId from webhook payload");
      return ResponseEntity.ok().build();
    }

    // Signal the Temporal workflow with the KYB result
    var signal = new KybResultSignal(result.kybId(), result.provider(), result.providerRef(), result.status().name(),
        result.riskSignals(), result.reviewNotes(), result.completedAt());

    var workflowStub = workflowClient.newWorkflowStub(MerchantOnboardingWorkflow.class, "onboarding-" + merchantId);
    workflowStub.kybResultReceived(signal);

    log.info("[WEBHOOK] KYB result signaled to workflow merchantId={} status={}", merchantId, result.status());
    return ResponseEntity.ok().build();
  }

  @SuppressWarnings("unchecked")
  private UUID resolveMerchantId(KybVerification result, Map<String, Object> payload) {
    if (result.merchantId() != null) {
      return result.merchantId();
    }
    var object = (Map<String, Object>) payload.get("object");
    if (object != null && object.containsKey("tags")) {
      var tags = object.get("tags");
      if (tags instanceof Iterable<?> tagList) {
        for (var tag : tagList) {
          var tagStr = tag.toString();
          if (tagStr.startsWith("merchant_id:")) {
            try {
              return UUID.fromString(tagStr.substring("merchant_id:".length()));
            } catch (IllegalArgumentException ignored) {
              // not a valid UUID
            }
          }
        }
      }
    }
    return null;
  }
}
