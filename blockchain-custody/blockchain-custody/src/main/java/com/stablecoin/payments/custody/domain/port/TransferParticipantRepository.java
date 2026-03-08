package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.TransferParticipant;

import java.util.List;
import java.util.UUID;

public interface TransferParticipantRepository {

    TransferParticipant save(TransferParticipant participant);

    List<TransferParticipant> findByTransferId(UUID transferId);
}
