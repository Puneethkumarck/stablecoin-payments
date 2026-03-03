package com.stablecoin.payments.gateway.iam.application.service;

import com.stablecoin.payments.gateway.iam.api.request.CreateMerchantRequest;
import com.stablecoin.payments.gateway.iam.api.response.MerchantResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.model.Corridor;
import com.stablecoin.payments.gateway.iam.domain.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MerchantApplicationService {

    private final MerchantService merchantService;
    private final GatewayResponseMapper mapper;

    public MerchantResponse createMerchant(CreateMerchantRequest request) {
        List<Corridor> corridors = request.corridors() != null
                ? request.corridors().stream()
                        .map(c -> new Corridor(c.sourceCountry(), c.targetCountry()))
                        .toList()
                : Collections.emptyList();

        var merchant = merchantService.register(
                request.externalId(),
                request.name(),
                request.country(),
                request.scopes() != null ? request.scopes() : Collections.emptyList(),
                corridors);

        return mapper.toMerchantResponse(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchant(UUID merchantId) {
        var merchant = merchantService.findById(merchantId);
        return mapper.toMerchantResponse(merchant);
    }
}
