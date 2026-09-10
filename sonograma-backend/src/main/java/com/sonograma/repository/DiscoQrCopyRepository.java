package com.sonograma.repository;

import com.sonograma.entity.DiscoQrCopy;
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

    List<DiscoQrCopy> findByIdDiscoAndEstadoOrderByCopyNumber(Long idDisco, EstadoCopiaDisco estado);

    List<DiscoQrCopy> findByManualDiscogsBatchIdOrderByCopyNumber(Long batchId);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DiscoQrCopy c WHERE c.id = :id")
    Optional<DiscoQrCopy> findByIdForUpdate(@Param("id") Long id);

    long countByIdDiscoAndEstado(Long idDisco, EstadoCopiaDisco estado);

    Optional<DiscoQrCopy> findByCodigoQr(String codigoQr);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DiscoQrCopy c WHERE c.id IN :ids")
    List<DiscoQrCopy> findAllByIdForUpdate(@Param("ids") List<Long> ids);
}
