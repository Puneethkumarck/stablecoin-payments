package com.stablecoin.payments.gateway.iam.infrastructure.persistence.mapper;

import com.stablecoin.payments.gateway.iam.domain.model.Corridor;
import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.MerchantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper
public interface MerchantEntityMapper {

    @Mapping(target = "scopes", expression = "java(toArray(merchant.getScopes()))")
    @Mapping(target = "corridors", expression = "java(toCorridorsJson(merchant.getCorridors()))")
    @Mapping(target = "status", expression = "java(merchant.getStatus().name())")
    @Mapping(target = "kybStatus", expression = "java(merchant.getKybStatus().name())")
    @Mapping(target = "rateLimitTier", expression = "java(merchant.getRateLimitTier().name())")
    @Mapping(target = "version", ignore = true)
    MerchantEntity toEntity(Merchant merchant);

    @Mapping(target = "scopes", expression = "java(toList(entity.getScopes()))")
    @Mapping(target = "corridors", expression = "java(fromCorridorsJson(entity.getCorridors()))")
    @Mapping(target = "status", expression = "java(com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus.valueOf(entity.getStatus()))")
    @Mapping(target = "kybStatus", expression = "java(com.stablecoin.payments.gateway.iam.domain.model.KybStatus.valueOf(entity.getKybStatus()))")
    @Mapping(target = "rateLimitTier", expression = "java(com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier.valueOf(entity.getRateLimitTier()))")
    Merchant toDomain(MerchantEntity entity);

    @Mapping(target = "merchantId", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "scopes", expression = "java(toArray(merchant.getScopes()))")
    @Mapping(target = "corridors", expression = "java(toCorridorsJson(merchant.getCorridors()))")
    @Mapping(target = "status", expression = "java(merchant.getStatus().name())")
    @Mapping(target = "kybStatus", expression = "java(merchant.getKybStatus().name())")
    @Mapping(target = "rateLimitTier", expression = "java(merchant.getRateLimitTier().name())")
    void updateEntity(Merchant merchant, @MappingTarget MerchantEntity entity);

    default String[] toArray(List<String> list) {
        if (list == null) {
            return new String[0];
        }
        return list.toArray(new String[0]);
    }

    default List<String> toList(String[] array) {
        if (array == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(array);
    }

    default String toCorridorsJson(List<Corridor> corridors) {
        if (corridors == null || corridors.isEmpty()) {
            return "[]";
        }
        try {
            return JsonMapper.builder().build().writeValueAsString(corridors);
        } catch (Exception e) {
            return "[]";
        }
    }

    default List<Corridor> fromCorridorsJson(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            var mapper = JsonMapper.builder().build();
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, Corridor.class));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
