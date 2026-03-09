package com.stablecoin.payments.offramp.domain.port;

import com.stablecoin.payments.offramp.domain.model.StablecoinRedemption;

import java.util.Optional;
import java.util.UUID;

public interface StablecoinRedemptionRepository {

    StablecoinRedemption save(StablecoinRedemption redemption);

    Optional<StablecoinRedemption> findById(UUID redemptionId);

    Optional<StablecoinRedemption> findByPayoutId(UUID payoutId);
}
