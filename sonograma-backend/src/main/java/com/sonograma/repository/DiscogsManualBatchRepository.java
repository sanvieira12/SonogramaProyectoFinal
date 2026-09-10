package com.sonograma.repository;

import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.DiscogsManualBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface DiscogsManualBatchRepository extends JpaRepository<DiscogsManualBatch, Long> {

    /**
     * Loads the technical batches and their physical-copy memberships so the
     * catalogue can project one logical source per normalized customer code.
     */
    @Query("""
            SELECT DISTINCT b
            FROM DiscogsManualBatch b
            LEFT JOIN FETCH b.copies
            ORDER BY b.createdAt DESC
            """)
    List<DiscogsManualBatch> findAllWithCopiesForCatalog();

    boolean existsByNormalizedCustomerCode(String normalizedCustomerCode);

    Optional<DiscogsManualBatch> findByNormalizedCustomerCodeAndStatus(
            String normalizedCustomerCode,
            DiscogsManualBatchStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM DiscogsManualBatch b
            WHERE b.normalizedCustomerCode = :normalizedCustomerCode
              AND b.status = :status
            """)
    Optional<DiscogsManualBatch> findByNormalizedCustomerCodeAndStatusForUpdate(
            @Param("normalizedCustomerCode") String normalizedCustomerCode,
            @Param("status") DiscogsManualBatchStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM DiscogsManualBatch b WHERE b.id = :id")
    Optional<DiscogsManualBatch> findByIdForUpdate(@Param("id") Long id);

    boolean existsByNormalizedCustomerCodeAndStatus(
            String normalizedCustomerCode,
            DiscogsManualBatchStatus status
    );
}
