package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.response.MerchantResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.model.KybStatus;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.service.MerchantService;
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
    private MerchantService merchantService;

    @Mock
    private GatewayResponseMapper mapper;

    @InjectMocks
    private MerchantController controller;

    @Test
    @DisplayName("createMerchant should return created merchant")
    void shouldCreateMerchant() {
        var merchant = aMerchant();
        var response = aMerchantResponse(merchant);
        given(merchantService.register(any(), any(), any(), any(), any())).willReturn(merchant);
        given(mapper.toMerchantResponse(merchant)).willReturn(response);

        var request = new com.stablecoin.payments.gateway.iam.api.request.CreateMerchantRequest(
                UUID.randomUUID(), "Test Co", "US", List.of("payments:read"), null);

        var result = controller.createMerchant(request);

        assertThat(result.name()).isEqualTo("Test Co");
    }

    @Test
    @DisplayName("getMerchant should return merchant by id")
    void shouldGetMerchant() {
        var merchant = aMerchant();
        var response = aMerchantResponse(merchant);
        given(merchantService.findById(merchant.getMerchantId())).willReturn(merchant);
        given(mapper.toMerchantResponse(merchant)).willReturn(response);

        var result = controller.getMerchant(merchant.getMerchantId());

        assertThat(result.merchantId()).isEqualTo(merchant.getMerchantId());
    }

    @Test
    @DisplayName("getMerchant should throw when not found")
    void shouldThrowWhenNotFound() {
        var id = UUID.randomUUID();
        given(merchantService.findById(id)).willThrow(MerchantNotFoundException.byId(id));

        assertThatThrownBy(() -> controller.getMerchant(id))
                .isInstanceOf(MerchantNotFoundException.class);
    }

    private Merchant aMerchant() {
        return Merchant.builder()
                .merchantId(UUID.randomUUID())
                .externalId(UUID.randomUUID())
                .name("Test Co")
                .country("US")
                .scopes(List.of("payments:read"))
                .corridors(List.of())
                .status(MerchantStatus.ACTIVE)
                .kybStatus(KybStatus.VERIFIED)
                .rateLimitTier(RateLimitTier.STARTER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    private MerchantResponse aMerchantResponse(Merchant m) {
        return new MerchantResponse(
                m.getMerchantId(), m.getExternalId(), m.getName(), m.getCountry(),
                m.getScopes(), "ACTIVE", "VERIFIED", "STARTER", m.getCreatedAt());
    }
}
