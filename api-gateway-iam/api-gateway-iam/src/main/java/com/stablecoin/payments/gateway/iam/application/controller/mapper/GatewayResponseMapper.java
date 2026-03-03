package com.stablecoin.payments.gateway.iam.application.controller.mapper;

import com.stablecoin.payments.gateway.iam.api.response.MerchantResponse;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface GatewayResponseMapper {

    @Mapping(target = "status", expression = "java(merchant.getStatus().name())")
    @Mapping(target = "kybStatus", expression = "java(merchant.getKybStatus().name())")
    @Mapping(target = "rateLimitTier", expression = "java(merchant.getRateLimitTier().name())")
    MerchantResponse toMerchantResponse(Merchant merchant);
}
