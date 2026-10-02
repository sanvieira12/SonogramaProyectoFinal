package com.sonograma.repository;

import com.sonograma.entity.ManualDiscogsSourceReconciliation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ManualDiscogsSourceReconciliationRepository
        extends JpaRepository<ManualDiscogsSourceReconciliation, Long> {

    Optional<ManualDiscogsSourceReconciliation> findByNormalizedSourceCustomerCode(String normalizedSourceCustomerCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM ManualDiscogsSourceReconciliation r
            WHERE r.normalizedSourceCustomerCode = :source
            """)
    Optional<ManualDiscogsSourceReconciliation> findByNormalizedSourceCustomerCodeForUpdate(
            @Param("source") String normalizedSourceCustomerCode);
}
