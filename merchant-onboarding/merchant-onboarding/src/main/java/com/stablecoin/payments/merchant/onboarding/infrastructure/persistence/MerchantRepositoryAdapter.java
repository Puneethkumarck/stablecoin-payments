package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.Merchant;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.MerchantRepository;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.PagedResult;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.MerchantJpaRepository;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.mapper.MerchantEntityMapper;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.mapper.MerchantEntityUpdater;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MerchantRepositoryAdapter implements MerchantRepository {

    private final MerchantJpaRepository jpa;
    private final MerchantEntityMapper mapper;
    private final MerchantEntityUpdater updater;

    @Override
    public Merchant save(Merchant merchant) {
        var existing = jpa.findById(merchant.getMerchantId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), merchant);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(merchant)));
    }

    @Override
    public Optional<Merchant> findById(UUID merchantId) {
        return jpa.findById(merchantId).map(mapper::toDomain);
    }

    @Override
    public Optional<Merchant> findByRegistrationNumberAndCountry(String registrationNumber, String country) {
        return jpa.findByRegistrationNumberAndRegistrationCountry(registrationNumber, country)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByRegistrationNumberAndCountry(String registrationNumber, String country) {
        return jpa.existsByRegistrationNumberAndRegistrationCountry(registrationNumber, country);
    }

    @Override
    public PagedResult<Merchant> findAll(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = jpa.findAll(pageable);
        return new PagedResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public PagedResult<Merchant> findAllByStatus(MerchantStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = jpa.findByStatus(status, pageable);
        return new PagedResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
