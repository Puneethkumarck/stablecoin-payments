package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.team.model.Invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {

    Optional<Invitation> findById(UUID invitationId);

    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findByMerchantId(UUID merchantId);

    Invitation save(Invitation invitation);
}
