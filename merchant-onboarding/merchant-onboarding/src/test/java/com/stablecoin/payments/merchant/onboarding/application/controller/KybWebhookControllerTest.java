package com.stablecoin.payments.merchant.onboarding.application.controller;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.KybProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybVerification;
import com.stablecoin.payments.merchant.onboarding.infrastructure.kyb.OnfidoWebhookValidator;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.workflow.MerchantOnboardingWorkflow;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("KybWebhookController")
class KybWebhookControllerTest {

  @Mock
  private KybProvider kybProvider;
  @Mock
  private WorkflowClient workflowClient;
  @Mock
  private OnfidoWebhookValidator webhookValidator;

  @InjectMocks
  private KybWebhookController controller;

  @Test
  @DisplayName("should return 401 when HMAC signature is invalid")
  void shouldReturn401WhenSignatureInvalid() {
    var body = "{\"action\":\"check.completed\"}";
    given(webhookValidator.isValid(body, "bad-sig")).willReturn(false);

    var response = controller.handleOnfidoWebhook(body, "bad-sig");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    then(kybProvider).should(never()).handleWebhook(any());
  }

  @Test
  @DisplayName("should return 200 and signal Temporal workflow on valid webhook")
  void shouldSignalWorkflowOnValidWebhook() {
    var merchantId = UUID.randomUUID();
    var body = "{\"action\":\"check.completed\",\"resource_type\":\"check\"}";
    var payload = Map.<String, Object>of("action", "check.completed", "resource_type", "check", "object",
        Map.of("id", "check-abc", "status", "complete", "href", "https://api.onfido.com/checks/check-abc"));
    var kybResult = KybVerification.builder().kybId(UUID.randomUUID()).merchantId(merchantId).provider("onfido")
        .providerRef("check-abc").status(KybStatus.PASSED).completedAt(Instant.now()).build();

    var workflowStub = mock(MerchantOnboardingWorkflow.class);
    given(webhookValidator.isValid(body, "valid-sig")).willReturn(true);
    given(webhookValidator.parsePayload(body)).willReturn(payload);
    given(kybProvider.handleWebhook(payload)).willReturn(kybResult);
    given(workflowClient.newWorkflowStub(eq(MerchantOnboardingWorkflow.class), eq("onboarding-" + merchantId)))
        .willReturn(workflowStub);

    var response = controller.handleOnfidoWebhook(body, "valid-sig");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    then(workflowStub).should().kybResultReceived(any());
  }

  @Test
  @DisplayName("should return 200 and skip processing when webhook is non-check event")
  void shouldSkipNonCheckEvent() {
    var body = "{\"action\":\"report.completed\",\"resource_type\":\"report\"}";
    var payload = Map.<String, Object>of("action", "report.completed", "resource_type", "report");

    given(webhookValidator.isValid(body, "sig")).willReturn(true);
    given(webhookValidator.parsePayload(body)).willReturn(payload);
    given(kybProvider.handleWebhook(payload)).willReturn(null);

    var response = controller.handleOnfidoWebhook(body, "sig");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    then(workflowClient).should(never()).newWorkflowStub(any(Class.class), any(String.class));
  }

  @Test
  @DisplayName("should return 200 when merchantId cannot be resolved")
  void shouldReturn200WhenMerchantIdCannotBeResolved() {
    var body = "{\"action\":\"check.completed\"}";
    var payload = Map.<String, Object>of("action", "check.completed", "resource_type", "check", "object",
        Map.of("id", "check-xyz", "status", "complete", "href", "https://api.onfido.com/checks/check-xyz"));
    var kybResult = KybVerification.builder().kybId(UUID.randomUUID()).merchantId(null).provider("onfido")
        .providerRef("check-xyz").status(KybStatus.PASSED).completedAt(Instant.now()).build();

    given(webhookValidator.isValid(body, "sig")).willReturn(true);
    given(webhookValidator.parsePayload(body)).willReturn(payload);
    given(kybProvider.handleWebhook(payload)).willReturn(kybResult);

    var response = controller.handleOnfidoWebhook(body, "sig");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    then(workflowClient).should(never()).newWorkflowStub(any(Class.class), any(String.class));
  }

  @Test
  @DisplayName("should resolve merchantId from payload tags when not in result")
  void shouldResolveMerchantIdFromPayloadTags() {
    var merchantId = UUID.randomUUID();
    var body = "{\"action\":\"check.completed\"}";
    var payload = Map.<String, Object>of("action", "check.completed", "resource_type", "check", "object",
        Map.of("id", "check-tag", "status", "complete", "href", "https://api.onfido.com/checks/check-tag", "tags",
            List.of("merchant_id:" + merchantId)));
    var kybResult = KybVerification.builder().kybId(UUID.randomUUID()).merchantId(null).provider("onfido")
        .providerRef("check-tag").status(KybStatus.PASSED).completedAt(Instant.now()).build();

    var workflowStub = mock(MerchantOnboardingWorkflow.class);
    given(webhookValidator.isValid(body, "sig")).willReturn(true);
    given(webhookValidator.parsePayload(body)).willReturn(payload);
    given(kybProvider.handleWebhook(payload)).willReturn(kybResult);
    given(workflowClient.newWorkflowStub(eq(MerchantOnboardingWorkflow.class), eq("onboarding-" + merchantId)))
        .willReturn(workflowStub);

    var response = controller.handleOnfidoWebhook(body, "sig");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    then(workflowStub).should().kybResultReceived(any());
  }
}
