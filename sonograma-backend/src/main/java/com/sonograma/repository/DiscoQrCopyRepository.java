package com.sonograma.repository;

import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.dto.ManualDiscogsExcelRowDTO;
import com.sonograma.enums.EstadoCopiaDisco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface DiscoQrCopyRepository extends JpaRepository<DiscoQrCopy, Long> {

    List<DiscoQrCopy> findByIdDiscoOrderByCopyNumber(Long idDisco);

    /** Catalog read model: fetches provenance without triggering lazy follow-up queries. */
    @Query("""
            SELECT c
            FROM DiscoQrCopy c
            LEFT JOIN FETCH c.manualDiscogsBatch
            WHERE c.idDisco = :idDisco
            ORDER BY c.copyNumber ASC, c.id ASC
            """)
    List<DiscoQrCopy> findDetailsByIdDisco(@Param("idDisco") Long idDisco);

    /** One bounded copy query for every product returned by Nueva Venta search. */
    @Query("""
            SELECT c
            FROM DiscoQrCopy c
            LEFT JOIN FETCH c.manualDiscogsBatch
            WHERE c.idDisco IN :productIds
              AND c.estado = com.sonograma.enums.EstadoCopiaDisco.DISPONIBLE
            ORDER BY c.idDisco ASC, c.copyNumber ASC, c.id ASC
            """)
    List<DiscoQrCopy> findAvailableSaleChoicesByProductIds(
            @Param("productIds") List<Long> productIds);

    List<DiscoQrCopy> findByIdDiscoAndEstadoOrderByCopyNumber(Long idDisco, EstadoCopiaDisco estado);

    Optional<DiscoQrCopy> findByIdDiscoAndCopyNumber(Long idDisco, Integer copyNumber);

    boolean existsByIdDiscoAndManualDiscogsBatchIsNotNull(Long idDisco);

    boolean existsByIdDiscoAndEstadoAndManualDiscogsBatchIsNotNull(
            Long idDisco, EstadoCopiaDisco estado);

    List<DiscoQrCopy> findByManualDiscogsBatchIdOrderByCopyNumber(Long batchId);

    /** Resolves sold-copy attribution and its batch in one fetch for profit reporting. */
    @Query("""
            SELECT c
            FROM DiscoQrCopy c
            LEFT JOIN FETCH c.manualDiscogsBatch
            WHERE c.id IN :ids
            """)
    List<DiscoQrCopy> findAllWithManualBatchByIdIn(@Param("ids") List<Long> ids);

    @Query("""
            SELECT c
            FROM DiscoQrCopy c
            JOIN c.manualDiscogsBatch b
            WHERE b.normalizedCustomerCode = :normalizedCustomerCode
            ORDER BY b.createdAt ASC, c.copyNumber ASC, c.id ASC
            """)
    List<DiscoQrCopy> findByManualCustomerCodeOrderByCopyNumber(
            @Param("normalizedCustomerCode") String normalizedCustomerCode
    );

    /** Phase 7 source export: one flat row per retained copy, across every technical batch. */
    @Query("""
            SELECT new com.sonograma.dto.ManualDiscogsExcelRowDTO(
                c.id, c.createdAt, c.precioVenta, c.condicionFisica, c.estado,
                b.normalizedCustomerCode,
                d.discogsReleaseId, d.discogsUrl, d.artista, d.album, d.genero)
            FROM DiscoQrCopy c
            JOIN c.manualDiscogsBatch b
            JOIN Disco d ON d.idDisco = c.idDisco
            WHERE b.normalizedCustomerCode = :source
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<ManualDiscogsExcelRowDTO> findExcelRowsByManualSource(
            @Param("source") String normalizedSourceCustomerCode);

    /** Retained same-source/release history in every lifecycle state. */
    @Query("""
            SELECT c
            FROM DiscoQrCopy c
            JOIN FETCH c.manualDiscogsBatch b
            JOIN Disco d ON d.idDisco = c.idDisco
            WHERE b.normalizedCustomerCode = :normalizedCustomerCode
              AND d.discogsReleaseId = :releaseId
            ORDER BY c.copyNumber ASC, c.id ASC
            """)
    List<DiscoQrCopy> findRetainedByManualSourceAndRelease(
            @Param("normalizedCustomerCode") String normalizedCustomerCode,
            @Param("releaseId") Long releaseId);

    /** Source-wide retained physical history in one aggregate query. */
    @Query(value = """
            SELECT COUNT(*) AS provablePhysicalCopyCount,
                   COALESCE(SUM(CASE WHEN c.estado = 'DISPONIBLE' THEN 1 ELSE 0 END), 0) AS availableCopyCount,
                   COALESCE(SUM(CASE WHEN c.estado = 'VENDIDO' THEN 1 ELSE 0 END), 0) AS soldCopyCount,
                   COALESCE(SUM(CASE WHEN c.estado = 'REMOVED' THEN 1 ELSE 0 END), 0) AS removedCopyCount,
                   COUNT(DISTINCT d.discogs_release_id) AS distinctReleaseCount
            FROM disco_qr_copy c
            JOIN discogs_manual_batch b ON b.id_discogs_manual_batch = c.id_discogs_manual_batch
            JOIN disco d ON d.id_disco = c.id_disco
            WHERE b.normalized_customer_code = :source
            """, nativeQuery = true)
    ManualDiscogsSourceCopyAggregateProjection aggregateRetainedByManualSource(
            @Param("source") String normalizedSourceCustomerCode);

    /** One row per release having more than one retained physical copy. */
    @Query("""
            SELECT COUNT(c)
            FROM DiscoQrCopy c
            JOIN c.manualDiscogsBatch b
            JOIN Disco d ON d.idDisco = c.idDisco
            WHERE b.normalizedCustomerCode = :source
              AND d.discogsReleaseId IS NOT NULL
            GROUP BY d.discogsReleaseId
            HAVING COUNT(c) > 1
            """)
    List<Long> countDuplicateReleaseGroupsByManualSource(@Param("source") String normalizedSourceCustomerCode);

    /** Finalization-only lock: freezes source copy states while its snapshot is captured. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM DiscoQrCopy c
            JOIN c.manualDiscogsBatch b
            WHERE b.normalizedCustomerCode = :source
            ORDER BY c.id
            """)
    List<DiscoQrCopy> lockRetainedByManualSource(@Param("source") String normalizedSourceCustomerCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DiscoQrCopy c WHERE c.id = :id")
    Optional<DiscoQrCopy> findByIdForUpdate(@Param("id") Long id);

    long countByIdDiscoAndEstado(Long idDisco, EstadoCopiaDisco estado);

    Optional<DiscoQrCopy> findByCodigoQr(String codigoQr);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DiscoQrCopy c WHERE c.codigoQr = :codigoQr")
    Optional<DiscoQrCopy> findByCodigoQrForUpdate(@Param("codigoQr") String codigoQr);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DiscoQrCopy c WHERE c.id IN :ids")
    List<DiscoQrCopy> findAllByIdForUpdate(@Param("ids") List<Long> ids);
}
