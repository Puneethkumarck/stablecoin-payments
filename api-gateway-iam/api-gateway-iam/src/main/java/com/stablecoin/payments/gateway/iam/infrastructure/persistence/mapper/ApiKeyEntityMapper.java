package com.stablecoin.payments.gateway.iam.infrastructure.persistence.mapper;

import com.stablecoin.payments.gateway.iam.domain.model.ApiKey;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.ApiKeyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper
public interface ApiKeyEntityMapper {

    @Mapping(target = "scopes", expression = "java(toArray(apiKey.getScopes()))")
    @Mapping(target = "allowedIps", expression = "java(toArray(apiKey.getAllowedIps()))")
    @Mapping(target = "environment", expression = "java(apiKey.getEnvironment().name())")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "version", ignore = true)
    ApiKeyEntity toEntity(ApiKey apiKey);

    @Mapping(target = "scopes", expression = "java(toList(entity.getScopes()))")
    @Mapping(target = "allowedIps", expression = "java(toList(entity.getAllowedIps()))")
    @Mapping(target = "environment", expression = "java(com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment.valueOf(entity.getEnvironment()))")
    @Mapping(target = "active", source = "active")
    ApiKey toDomain(ApiKeyEntity entity);

    @Mapping(target = "keyId", ignore = true)
    @Mapping(target = "merchantId", ignore = true)
    @Mapping(target = "keyHash", ignore = true)
    @Mapping(target = "keyPrefix", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "scopes", expression = "java(toArray(apiKey.getScopes()))")
    @Mapping(target = "allowedIps", expression = "java(toArray(apiKey.getAllowedIps()))")
    @Mapping(target = "environment", expression = "java(apiKey.getEnvironment().name())")
    @Mapping(target = "active", source = "active")
    void updateEntity(ApiKey apiKey, @MappingTarget ApiKeyEntity entity);

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
}
