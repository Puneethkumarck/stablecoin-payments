package com.stablecoin.payments.offramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.offramp.domain.model.StablecoinRedemption;
import com.stablecoin.payments.offramp.domain.model.StablecoinTicker;
import com.stablecoin.payments.offramp.infrastructure.persistence.entity.StablecoinRedemptionEntity;
import org.mapstruct.Mapper;

@Mapper
public interface StablecoinRedemptionPersistenceMapper {

    default StablecoinRedemptionEntity toEntity(StablecoinRedemption redemption) {
        if (redemption == null) {
            return null;
        }
        return StablecoinRedemptionEntity.builder()
                .redemptionId(redemption.redemptionId())
                .payoutId(redemption.payoutId())
                .stablecoin(redemption.stablecoin() != null ? redemption.stablecoin().ticker() : null)
                .redeemedAmount(redemption.redeemedAmount())
                .fiatReceived(redemption.fiatReceived())
                .fiatCurrency(redemption.fiatCurrency())
                .partner(redemption.partner())
                .partnerReference(redemption.partnerReference())
                .redeemedAt(redemption.redeemedAt())
                .build();
    }

    default StablecoinRedemption toDomain(StablecoinRedemptionEntity entity) {
        if (entity == null) {
            return null;
        }

        StablecoinTicker stablecoin = entity.getStablecoin() != null
                ? StablecoinTicker.of(entity.getStablecoin())
                : null;

        return new StablecoinRedemption(
                entity.getRedemptionId(),
                entity.getPayoutId(),
                stablecoin,
                entity.getRedeemedAmount(),
                entity.getFiatReceived(),
                entity.getFiatCurrency(),
                entity.getPartner(),
                entity.getPartnerReference(),
                entity.getRedeemedAt()
        );
    }
}
