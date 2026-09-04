package com.sonograma.repository;

import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.DiscogsManualBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface DiscogsManualBatchRepository extends JpaRepository<DiscogsManualBatch, Long> {

    @Query("""
            SELECT new com.sonograma.dto.DiscogsCatalogSourceDTO(
                b.customerCode,
                COUNT(c.id),
                b.status,
                b.id,
                b.createdAt
            )
            FROM DiscogsManualBatch b
            LEFT JOIN b.copies c
            GROUP BY b.id, b.customerCode, b.status, b.createdAt
            ORDER BY b.createdAt DESC
            """)
    java.util.List<com.sonograma.dto.DiscogsCatalogSourceDTO> findCatalogSources();

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
