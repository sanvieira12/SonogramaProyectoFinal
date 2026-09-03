package com.sonograma.repository;

import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.enums.DiscogsImportRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;

public interface DiscogsImportRowRepository extends JpaRepository<DiscogsImportRow, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DiscogsImportRow r WHERE r.idDiscogsImportRow = :id")
    java.util.Optional<DiscogsImportRow> findByIdForUpdate(@Param("id") Long id);

    List<DiscogsImportRow> findByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(Long jobId);

    @EntityGraph(attributePaths = "importedCatalogProduct")
    List<DiscogsImportRow> findWithCatalogByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(Long jobId);

    List<DiscogsImportRow> findByJobIdDiscogsImportJobAndStatusInOrderBySourceExcelRowNumber(
            Long jobId,
            Collection<DiscogsImportRowStatus> statuses
    );

    @Query("""
            SELECT r FROM DiscogsImportRow r
            JOIN FETCH r.importedCatalogProduct p
            JOIN r.job j
            WHERE j.sourceFingerprint = :fingerprint
              AND j.idDiscogsImportJob <> :jobId
              AND r.sourceExcelRowNumber = :rowNumber
              AND r.discogsType = :discogsType
              AND r.discogsId = :discogsId
            ORDER BY j.createdAt DESC
            """)
    List<DiscogsImportRow> findPriorImportedRows(
            @Param("fingerprint") String fingerprint,
            @Param("jobId") Long jobId,
            @Param("rowNumber") Integer rowNumber,
            @Param("discogsType") String discogsType,
            @Param("discogsId") Long discogsId
    );

    @Query("""
            SELECT r FROM DiscogsImportRow r
            JOIN FETCH r.importedCatalogProduct p
            JOIN r.job j
            WHERE j.sourceFingerprint = :fingerprint
              AND j.idDiscogsImportJob <> :jobId
              AND r.sourceExcelRowNumber = :rowNumber
            ORDER BY j.createdAt DESC
            """)
    List<DiscogsImportRow> findPriorImportedPhysicalRows(
            @Param("fingerprint") String fingerprint,
            @Param("jobId") Long jobId,
            @Param("rowNumber") Integer rowNumber
    );

    @EntityGraph(attributePaths = "importedCatalogProduct")
    List<DiscogsImportRow> findByResolvedReleaseIdAndImportedCatalogProductIsNotNullOrderByIdDiscogsImportRowDesc(
            Long resolvedReleaseId
    );
}
