package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantJpaRepository extends JpaRepository<MerchantEntity, UUID> {

    Optional<MerchantEntity> findByRegistrationNumberAndRegistrationCountry(
            String registrationNumber, String registrationCountry);

    boolean existsByRegistrationNumberAndRegistrationCountry(
            String registrationNumber, String registrationCountry);

    Page<MerchantEntity> findByStatus(MerchantStatus status, Pageable pageable);
}
