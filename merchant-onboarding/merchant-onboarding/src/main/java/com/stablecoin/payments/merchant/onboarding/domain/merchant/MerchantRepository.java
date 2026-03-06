package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.PagedResult;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository {
    Merchant save(Merchant merchant);
    Optional<Merchant> findById(UUID merchantId);
    Optional<Merchant> findByRegistrationNumberAndCountry(String registrationNumber, String country);
    boolean existsByRegistrationNumberAndCountry(String registrationNumber, String country);
    PagedResult<Merchant> findAll(int page, int size);
    PagedResult<Merchant> findAllByStatus(MerchantStatus status, int page, int size);
}
