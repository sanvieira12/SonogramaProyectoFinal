package com.sonograma.repository;

import com.sonograma.entity.ManualDiscogsImportOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import com.sonograma.enums.ManualDiscogsImportOperationStatus;

public interface ManualDiscogsImportOperationRepository
        extends JpaRepository<ManualDiscogsImportOperation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ManualDiscogsImportOperation o WHERE o.operationId = :operationId")
    Optional<ManualDiscogsImportOperation> findByOperationIdForUpdate(@Param("operationId") UUID operationId);

    @Query("""
            SELECT DISTINCT o FROM ManualDiscogsImportOperation o
            LEFT JOIN FETCH o.manualBatch
            LEFT JOIN FETCH o.resultingCopies
            WHERE o.operationId = :operationId
            """)
    Optional<ManualDiscogsImportOperation> findWithLineageByOperationId(@Param("operationId") UUID operationId);

    List<ManualDiscogsImportOperation>
    findTop50ByNormalizedSourceCustomerCodeAndStatusOrderByCreatedAtDesc(
            String normalizedSourceCustomerCode,
            ManualDiscogsImportOperationStatus status);

    long countByNormalizedSourceCustomerCodeAndStatus(
            String normalizedSourceCustomerCode,
            ManualDiscogsImportOperationStatus status);

    @Query("""
            SELECT o.status AS status, COUNT(o) AS operationCount
            FROM ManualDiscogsImportOperation o
            WHERE o.normalizedSourceCustomerCode = :source
            GROUP BY o.status
            """)
    List<ManualDiscogsOperationStatusCountProjection> countStatusesByNormalizedSource(
            @Param("source") String normalizedSourceCustomerCode);

    @Query("""
            SELECT DISTINCT o FROM ManualDiscogsImportOperation o
            JOIN o.resultingCopies c
            LEFT JOIN FETCH o.manualBatch
            LEFT JOIN FETCH o.resultingCopies
            WHERE c.id = :copyId
            """)
    Optional<ManualDiscogsImportOperation> findCreatingOperationByCopyId(@Param("copyId") Long copyId);
}
