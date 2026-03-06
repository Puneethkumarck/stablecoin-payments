package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.KybProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.DocumentType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybVerification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Onfido sandbox/production KYB adapter.
 * <p>
 * Uses Onfido REST API v3.6 to create applicants and submit checks. In sandbox mode, results are deterministic:
 * <ul>
 * <li>{@code last_name = "Consider"} → consider (maps to MANUAL_REVIEW)</li>
 * <li>Any other last_name → clear (maps to PASSED)</li>
 * </ul>
 * <p>
 * Webhook callback completes the async flow via {@code handleWebhook()}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kyb.provider", havingValue = "onfido")
public class OnfidoKybAdapter implements KybProvider {

  private final RestClient restClient;
  private final OnfidoProperties properties;

  public OnfidoKybAdapter(OnfidoProperties properties) {
    this.properties = properties;
    var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    this.restClient = RestClient.builder().baseUrl(properties.baseUrl())
        .requestFactory(new JdkClientHttpRequestFactory(httpClient))
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Token token=" + properties.apiToken())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
  }

  @Override
  public KybVerification submit(UUID merchantId, String legalName, String registrationNumber, String country) {
    log.info("[ONFIDO] Creating applicant for merchant={} legalName={}", merchantId, legalName);

    // Step 1: Create an applicant
    var nameParts = splitName(legalName);
    var applicantBody = Map.of("first_name", nameParts[0], "last_name", nameParts[1]);

    @SuppressWarnings("unchecked")
    var applicantResponse = restClient.post().uri("/applicants").body(applicantBody).retrieve().body(Map.class);

    var applicantId = (String) applicantResponse.get("id");
    log.info("[ONFIDO] Applicant created applicantId={}", applicantId);

    // Step 2: Create a check
    var checkBody = Map.of("applicant_id", applicantId, "report_names", List.of("document", "watchlist_standard"));

    @SuppressWarnings("unchecked")
    var checkResponse = restClient.post().uri("/checks").body(checkBody).retrieve().body(Map.class);

    var checkId = (String) checkResponse.get("id");
    var checkStatus = (String) checkResponse.get("status");
    log.info("[ONFIDO] Check created checkId={} status={}", checkId, checkStatus);

    return KybVerification.builder().kybId(UUID.randomUUID()).merchantId(merchantId).provider("onfido")
        .providerRef(checkId).status(KybStatus.IN_PROGRESS)
        .riskSignals(Map.of("onfido_check_id", checkId, "applicant_id", applicantId))
        .documentsRequired(getRequiredDocuments(country, null)).initiatedAt(Instant.now()).build();
  }

  @Override
  public Optional<KybVerification> getResult(String providerRef) {
    log.info("[ONFIDO] Getting check result providerRef={}", providerRef);

    try {
      @SuppressWarnings("unchecked")
      var checkResponse = restClient.get().uri("/checks/{checkId}", providerRef).retrieve().body(Map.class);

      var status = (String) checkResponse.get("status");
      var result = (String) checkResponse.get("result");

      if (!"complete".equals(status)) {
        return Optional.of(KybVerification.builder().providerRef(providerRef).provider("onfido")
            .status(KybStatus.IN_PROGRESS).build());
      }

      return Optional.of(mapCheckResult(providerRef, result, checkResponse));
    } catch (Exception ex) {
      log.error("[ONFIDO] Failed to get check result providerRef={}", providerRef, ex);
      return Optional.empty();
    }
  }

  @Override
  public KybVerification handleWebhook(Map<String, Object> payload) {
    var resourceType = (String) payload.get("resource_type");
    var action = (String) payload.get("action");

    @SuppressWarnings("unchecked")
    var object = (Map<String, Object>) payload.get("object");
    var checkId = (String) object.get("id");
    var checkStatus = (String) object.get("status");
    var href = (String) object.get("href");

    log.info("[ONFIDO] Webhook received resourceType={} action={} checkId={} status={}", resourceType, action, checkId,
        checkStatus);

    if (!"check".equals(resourceType) || !"check.completed".equals(action)) {
      log.debug("[ONFIDO] Ignoring non-check webhook resourceType={} action={}", resourceType, action);
      return null;
    }

    // Fetch full check details
    @SuppressWarnings("unchecked")
    var checkResponse = restClient.get().uri("/checks/{checkId}", checkId).retrieve().body(Map.class);

    var result = (String) checkResponse.get("result");
    return mapCheckResult(checkId, result, checkResponse);
  }

  @Override
  public List<DocumentType> getRequiredDocuments(String country, String entityType) {
    return List.of(DocumentType.CERTIFICATE_OF_INCORPORATION, DocumentType.PROOF_OF_ADDRESS,
        DocumentType.BENEFICIAL_OWNER_DECLARATION);
  }

  private KybVerification mapCheckResult(String checkId, String result, Map<String, Object> checkResponse) {
    var kybStatus = mapOnfidoResult(result);
    var riskSignals = buildRiskSignals(result, checkResponse);

    return KybVerification.builder().kybId(UUID.randomUUID()).provider("onfido").providerRef(checkId).status(kybStatus)
        .riskSignals(riskSignals).completedAt(Instant.now()).build();
  }

  private KybStatus mapOnfidoResult(String result) {
    if (result == null) {
      return KybStatus.IN_PROGRESS;
    }
    return switch (result) {
      case "clear" -> KybStatus.PASSED;
      case "consider" -> KybStatus.MANUAL_REVIEW;
      default -> KybStatus.FAILED;
    };
  }

  private Map<String, Object> buildRiskSignals(String result, Map<String, Object> checkResponse) {
    var signals = new HashMap<String, Object>();
    signals.put("onfido_result", result);
    signals.put("provider", "onfido");

    // Map result to risk_score for RiskTierCalculator
    int riskScore = switch (result != null ? result : "") {
      case "clear" -> 0;
      case "consider" -> 35;
      default -> 60;
    };
    signals.put("risk_score", riskScore);

    if (checkResponse.containsKey("tags")) {
      signals.put("tags", checkResponse.get("tags"));
    }
    return signals;
  }

  private String[] splitName(String fullName) {
    if (fullName == null || fullName.isBlank()) {
      return new String[]{"Unknown", "Unknown"};
    }
    var parts = fullName.trim().split("\\s+", 2);
    if (parts.length == 1) {
      return new String[]{parts[0], parts[0]};
    }
    return parts;
  }
}
