package com.stablecoin.payments.custody.infrastructure.provider.fireblocks;

record FireblocksCreateTransactionRequest(
        String assetId,
        TransferPeerPath source,
        DestinationTransferPeerPath destination,
        String amount,
        String note,
        String externalTxId
) {

    record TransferPeerPath(String type, String id) {}

    record DestinationTransferPeerPath(String type, OneTimeAddress oneTimeAddress) {}

    record OneTimeAddress(String address) {}
}
