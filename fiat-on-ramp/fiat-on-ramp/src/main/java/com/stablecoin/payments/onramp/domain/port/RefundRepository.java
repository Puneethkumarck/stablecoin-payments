package com.stablecoin.payments.onramp.domain.port;

import com.stablecoin.payments.onramp.domain.model.Refund;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {

    Refund save(Refund refund);

    Optional<Refund> findById(UUID refundId);

    List<Refund> findByCollectionId(UUID collectionId);
}
