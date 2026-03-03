package com.stablecoin.payments.gateway.iam.infrastructure.persistence.mapper;

import com.stablecoin.payments.gateway.iam.domain.model.OAuthClient;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.OAuthClientEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper
public interface OAuthClientEntityMapper {

    @Mapping(target = "scopes", expression = "java(toArray(client.getScopes()))")
    @Mapping(target = "grantTypes", expression = "java(toArray(client.getGrantTypes()))")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "version", ignore = true)
    OAuthClientEntity toEntity(OAuthClient client);

    @Mapping(target = "scopes", expression = "java(toList(entity.getScopes()))")
    @Mapping(target = "grantTypes", expression = "java(toList(entity.getGrantTypes()))")
    @Mapping(target = "active", source = "active")
    OAuthClient toDomain(OAuthClientEntity entity);

    @Mapping(target = "clientId", ignore = true)
    @Mapping(target = "merchantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "scopes", expression = "java(toArray(client.getScopes()))")
    @Mapping(target = "grantTypes", expression = "java(toArray(client.getGrantTypes()))")
    @Mapping(target = "active", source = "active")
    void updateEntity(OAuthClient client, @MappingTarget OAuthClientEntity entity);

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
