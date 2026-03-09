package com.stablecoin.payments.onramp.infrastructure.persistence.entity;

import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionOrderJpaRepository extends JpaRepository<CollectionOrderEntity, UUID> {

    Optional<CollectionOrderEntity> findByPaymentId(UUID paymentId);

    Optional<CollectionOrderEntity> findByPspReference(String pspReference);

    @Query("SELECT o FROM CollectionOrderEntity o WHERE o.status = :status "
            + "AND o.collectionId NOT IN "
            + "(SELECT r.collectionId FROM ReconciliationRecordEntity r)")
    List<CollectionOrderEntity> findByStatusAndNotReconciled(@Param("status") CollectionStatus status);

    @Query("SELECT o FROM CollectionOrderEntity o WHERE o.status = :status AND o.expiresAt < :before")
    List<CollectionOrderEntity> findExpiredByStatus(@Param("status") CollectionStatus status,
                                                    @Param("before") Instant before);
}
