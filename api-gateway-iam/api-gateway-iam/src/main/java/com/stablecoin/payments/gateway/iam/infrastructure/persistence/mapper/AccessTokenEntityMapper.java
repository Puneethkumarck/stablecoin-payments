package com.stablecoin.payments.gateway.iam.infrastructure.persistence.mapper;

import com.stablecoin.payments.gateway.iam.domain.model.AccessToken;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.AccessTokenEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper
public interface AccessTokenEntityMapper {

    @Mapping(target = "scopes", expression = "java(toArray(token.getScopes()))")
    AccessTokenEntity toEntity(AccessToken token);

    @Mapping(target = "scopes", expression = "java(toList(entity.getScopes()))")
    AccessToken toDomain(AccessTokenEntity entity);

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
