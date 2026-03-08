package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.TransferStatus;
import com.stablecoin.payments.custody.domain.model.TransferType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChainTransferRepository {

    ChainTransfer save(ChainTransfer transfer);

    Optional<ChainTransfer> findById(UUID transferId);

    Optional<ChainTransfer> findByPaymentIdAndType(UUID paymentId, TransferType type);

    List<ChainTransfer> findByStatus(TransferStatus status);
}
