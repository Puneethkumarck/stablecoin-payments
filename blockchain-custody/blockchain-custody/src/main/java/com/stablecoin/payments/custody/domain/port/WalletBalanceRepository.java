package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.WalletBalance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletBalanceRepository {

    WalletBalance save(WalletBalance balance);

    Optional<WalletBalance> findByWalletIdAndStablecoin(UUID walletId, StablecoinTicker stablecoin);

    Optional<WalletBalance> findByWalletIdAndStablecoinForUpdate(UUID walletId, StablecoinTicker stablecoin);

    List<WalletBalance> findByWalletId(UUID walletId);

    List<WalletBalance> findAll();
}
