package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.mapper;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.ApprovedCorridor;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.ApprovedCorridorEntity;
import org.mapstruct.Mapper;

@Mapper
public interface ApprovedCorridorEntityMapper {

    ApprovedCorridorEntity toEntity(ApprovedCorridor corridor);

    ApprovedCorridor toDomain(ApprovedCorridorEntity entity);
}
