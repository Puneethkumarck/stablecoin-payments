package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.CreateMerchantRequest;
import com.stablecoin.payments.gateway.iam.api.response.MerchantResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.model.Corridor;
import com.stablecoin.payments.gateway.iam.domain.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final GatewayResponseMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantResponse createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        log.info("Create merchant externalId={} name={}", request.externalId(), request.name());

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

    @GetMapping("/{merchantId}")
    public MerchantResponse getMerchant(@PathVariable UUID merchantId) {
        var merchant = merchantService.findById(merchantId);
        return mapper.toMerchantResponse(merchant);
    }
}
