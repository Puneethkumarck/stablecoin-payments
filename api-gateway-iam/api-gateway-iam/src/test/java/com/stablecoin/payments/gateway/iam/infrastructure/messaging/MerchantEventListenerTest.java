package com.stablecoin.payments.gateway.iam.infrastructure.messaging;

import com.stablecoin.payments.gateway.iam.application.service.MerchantApplicationService;
import com.stablecoin.payments.gateway.iam.domain.service.MerchantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantEventListener")
class MerchantEventListenerTest {

    @Mock private MerchantService merchantService;
    @Mock private MerchantApplicationService merchantApplicationService;

    @InjectMocks
    private MerchantEventListener listener;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    MerchantEventListenerTest() throws Exception {
        // JsonMapper needs to be set via reflection since @InjectMocks won't match it
    }

    @Test
    @DisplayName("onMerchantActivated should activate and provision OAuth client")
    void shouldActivateAndProvisionOAuthClient() throws Exception {
        var merchantId = UUID.randomUUID();
        var payload = """
                {"merchantId":"%s","companyName":"Acme Corp","country":"US","scopes":["payments:read","payments:write"]}
                """.formatted(merchantId);

        // Use a real ObjectMapper for deserialization, mock for application service
        var realListener = new MerchantEventListener(
                merchantService, merchantApplicationService, objectMapper);

        realListener.onMerchantActivated(payload);

        then(merchantApplicationService).should().activateAndProvisionOAuthClient(
                merchantId, "Acme Corp", List.of("payments:read", "payments:write"));
    }

    @Test
    @DisplayName("onMerchantActivated should propagate exception on failure")
    void shouldPropagateExceptionOnActivationFailure() {
        var merchantId = UUID.randomUUID();
        var payload = """
                {"merchantId":"%s","companyName":"Acme Corp","country":"US","scopes":[]}
                """.formatted(merchantId);

        doThrow(new RuntimeException("activation failed"))
                .when(merchantApplicationService)
                .activateAndProvisionOAuthClient(merchantId, "Acme Corp", List.of());

        var realListener = new MerchantEventListener(
                merchantService, merchantApplicationService, objectMapper);

        assertThatThrownBy(() -> realListener.onMerchantActivated(payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("merchant.activated");
    }
}
