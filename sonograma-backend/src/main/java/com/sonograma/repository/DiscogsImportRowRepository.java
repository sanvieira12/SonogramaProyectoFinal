package com.sonograma.repository;

import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.entity.Disco;
import com.sonograma.dto.DiscogsCatalogJobFilterDTO;
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
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM DiscogsImportRow r
            WHERE r.job.idDiscogsImportJob = :jobId
              AND r.importedCatalogProduct.idDisco = :productId
              AND r.catalogProductResult = 'NEW_PRODUCT'
            """)
    boolean hasNewProductReceiptInJob(
            @Param("jobId") Long jobId,
            @Param("productId") Long productId
    );

    @EntityGraph(attributePaths = "importedCatalogProduct")
    List<DiscogsImportRow> findByResolvedReleaseIdAndImportedCatalogProductIsNotNullOrderByIdDiscogsImportRowDesc(
            Long resolvedReleaseId
    );

    @Query("""
            SELECT DISTINCT p
            FROM DiscogsImportRow r
            JOIN r.importedCatalogProduct p
            WHERE r.job.idDiscogsImportJob = :jobId
              AND r.status = com.sonograma.enums.DiscogsImportRowStatus.IMPORTED
              AND p.catalogDeletedAt IS NULL
            """)
    List<Disco> findDistinctActiveCatalogProductsByJobId(@Param("jobId") Long jobId);

    @Query("""
            SELECT DISTINCT p
            FROM DiscogsImportRow r
            JOIN r.importedCatalogProduct p
            WHERE r.job.idDiscogsImportJob IN :jobIds
              AND r.status = com.sonograma.enums.DiscogsImportRowStatus.IMPORTED
              AND p.catalogDeletedAt IS NULL
            """)
    List<Disco> findDistinctActiveCatalogProductsByJobIds(@Param("jobIds") Collection<Long> jobIds);

    @Query("""
            SELECT new com.sonograma.dto.DiscogsCatalogJobFilterDTO(
                r.job.idDiscogsImportJob,
                r.job.nombreArchivo,
                r.job.createdAt,
                COUNT(DISTINCT p.idDisco)
            )
            FROM DiscogsImportRow r
            JOIN r.importedCatalogProduct p
            WHERE r.status = com.sonograma.enums.DiscogsImportRowStatus.IMPORTED
              AND p.catalogDeletedAt IS NULL
            GROUP BY r.job.idDiscogsImportJob, r.job.nombreArchivo, r.job.createdAt
            ORDER BY r.job.createdAt DESC
            """)
    List<DiscogsCatalogJobFilterDTO> findCatalogJobFilters();
}
