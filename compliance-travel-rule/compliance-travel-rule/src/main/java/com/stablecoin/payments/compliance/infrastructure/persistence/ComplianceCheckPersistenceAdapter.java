package com.stablecoin.payments.compliance.infrastructure.persistence;

import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.port.ComplianceCheckRepository;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.AmlResultEntity;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.AmlResultJpaRepository;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.ComplianceCheckEntity;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.ComplianceCheckJpaRepository;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.KycResultEntity;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.KycResultJpaRepository;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.SanctionsResultEntity;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.SanctionsResultJpaRepository;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.TravelRulePackageEntity;
import com.stablecoin.payments.compliance.infrastructure.persistence.entity.TravelRulePackageJpaRepository;
import com.stablecoin.payments.compliance.infrastructure.persistence.mapper.AmlResultPersistenceMapper;
import com.stablecoin.payments.compliance.infrastructure.persistence.mapper.ComplianceCheckEntityUpdater;
import com.stablecoin.payments.compliance.infrastructure.persistence.mapper.ComplianceCheckPersistenceMapper;
import com.stablecoin.payments.compliance.infrastructure.persistence.mapper.KycResultPersistenceMapper;
import com.stablecoin.payments.compliance.infrastructure.persistence.mapper.SanctionsResultPersistenceMapper;
import com.stablecoin.payments.compliance.infrastructure.persistence.mapper.TravelRulePackagePersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ComplianceCheckPersistenceAdapter implements ComplianceCheckRepository {

    private final ComplianceCheckJpaRepository checkJpa;
    private final KycResultJpaRepository kycJpa;
    private final SanctionsResultJpaRepository sanctionsJpa;
    private final AmlResultJpaRepository amlJpa;
    private final TravelRulePackageJpaRepository travelRuleJpa;
    private final ComplianceCheckPersistenceMapper mapper;
    private final ComplianceCheckEntityUpdater updater;
    private final KycResultPersistenceMapper kycMapper;
    private final SanctionsResultPersistenceMapper sanctionsMapper;
    private final AmlResultPersistenceMapper amlMapper;
    private final TravelRulePackagePersistenceMapper travelRuleMapper;

    @Override
    public ComplianceCheck save(ComplianceCheck check) {
        var existingCheck = checkJpa.findById(check.checkId());
        ComplianceCheckEntity savedCheck;
        if (existingCheck.isPresent()) {
            updater.updateEntity(existingCheck.get(), check);
            savedCheck = checkJpa.save(existingCheck.get());
        } else {
            savedCheck = checkJpa.save(mapper.toEntity(check));
        }

        KycResultEntity savedKyc = null;
        if (check.kycResult() != null) {
            var existingKyc = kycJpa.findByCheckId(check.checkId());
            if (existingKyc.isPresent()) {
                savedKyc = existingKyc.get();
            } else {
                var kycEntity = kycMapper.toEntity(check.kycResult());
                kycEntity.setCheckId(check.checkId());
                savedKyc = kycJpa.save(kycEntity);
            }
        }

        SanctionsResultEntity savedSanctions = null;
        if (check.sanctionsResult() != null) {
            var existingSanctions = sanctionsJpa.findByCheckId(check.checkId());
            if (existingSanctions.isPresent()) {
                savedSanctions = existingSanctions.get();
            } else {
                var sanctionsEntity = sanctionsMapper.toEntity(check.sanctionsResult());
                sanctionsEntity.setCheckId(check.checkId());
                savedSanctions = sanctionsJpa.save(sanctionsEntity);
            }
        }

        AmlResultEntity savedAml = null;
        if (check.amlResult() != null) {
            var existingAml = amlJpa.findByCheckId(check.checkId());
            if (existingAml.isPresent()) {
                savedAml = existingAml.get();
            } else {
                var amlEntity = amlMapper.toEntity(check.amlResult());
                amlEntity.setCheckId(check.checkId());
                savedAml = amlJpa.save(amlEntity);
            }
        }

        TravelRulePackageEntity savedTravelRule = null;
        if (check.travelRulePackage() != null) {
            var existingTravelRule = travelRuleJpa.findByCheckId(check.checkId());
            if (existingTravelRule.isPresent()) {
                savedTravelRule = existingTravelRule.get();
            } else {
                savedTravelRule = travelRuleJpa.save(travelRuleMapper.toEntity(check.travelRulePackage()));
            }
        }

        return mapper.toDomain(savedCheck, savedKyc, savedSanctions, savedAml, savedTravelRule);
    }

    @Override
    public Optional<ComplianceCheck> findById(UUID checkId) {
        return checkJpa.findById(checkId).map(this::toDomainAggregate);
    }

    @Override
    public Optional<ComplianceCheck> findByPaymentId(UUID paymentId) {
        return checkJpa.findByPaymentId(paymentId).map(this::toDomainAggregate);
    }

    private ComplianceCheck toDomainAggregate(ComplianceCheckEntity entity) {
        var kycResult = kycJpa.findByCheckId(entity.getCheckId()).orElse(null);
        var sanctionsResult = sanctionsJpa.findByCheckId(entity.getCheckId()).orElse(null);
        var amlResult = amlJpa.findByCheckId(entity.getCheckId()).orElse(null);
        var travelRule = travelRuleJpa.findByCheckId(entity.getCheckId()).orElse(null);
        return mapper.toDomain(entity, kycResult, sanctionsResult, amlResult, travelRule);
    }
}
