package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.response.MerchantResponse;
import com.stablecoin.payments.gateway.iam.application.service.MerchantApplicationService;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantController")
class MerchantControllerTest {

    @Mock
    private MerchantApplicationService merchantApplicationService;

    @InjectMocks
    private MerchantController controller;

    @Test
    @DisplayName("createMerchant should return created merchant")
    void shouldCreateMerchant() {
        var merchantId = UUID.randomUUID();
        var response = new MerchantResponse(
                merchantId, UUID.randomUUID(), "Test Co", "US",
                List.of("payments:read"), "ACTIVE", "VERIFIED", "STARTER", Instant.now());
        given(merchantApplicationService.createMerchant(any())).willReturn(response);

        var request = new com.stablecoin.payments.gateway.iam.api.request.CreateMerchantRequest(
                UUID.randomUUID(), "Test Co", "US", List.of("payments:read"), null);

        var result = controller.createMerchant(request);

        assertThat(result.name()).isEqualTo("Test Co");
    }

    @Test
    @DisplayName("getMerchant should return merchant by id")
    void shouldGetMerchant() {
        var merchantId = UUID.randomUUID();
        var response = new MerchantResponse(
                merchantId, UUID.randomUUID(), "Test Co", "US",
                List.of("payments:read"), "ACTIVE", "VERIFIED", "STARTER", Instant.now());
        given(merchantApplicationService.getMerchant(merchantId)).willReturn(response);

        var result = controller.getMerchant(merchantId);

        assertThat(result.merchantId()).isEqualTo(merchantId);
    }

    @Test
    @DisplayName("getMerchant should throw when not found")
    void shouldThrowWhenNotFound() {
        var id = UUID.randomUUID();
        given(merchantApplicationService.getMerchant(id)).willThrow(MerchantNotFoundException.byId(id));

        assertThatThrownBy(() -> controller.getMerchant(id))
                .isInstanceOf(MerchantNotFoundException.class);
    }
}
