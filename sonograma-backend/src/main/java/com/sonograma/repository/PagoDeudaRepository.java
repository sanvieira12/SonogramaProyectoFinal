package com.sonograma.repository;

import com.sonograma.entity.PagoDeuda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PagoDeudaRepository extends JpaRepository<PagoDeuda, Long> {
    @Query("""
        SELECT p FROM PagoDeuda p
        WHERE p.deuda.idDeuda = :idDeuda
          AND COALESCE(p.anulado, false) = false
        ORDER BY p.fechaPago DESC, p.createdAt DESC
        """)
    List<PagoDeuda> findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(@Param("idDeuda") Long idDeuda);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PagoDeuda p WHERE p.idPagoDeuda = :idPagoDeuda")
    Optional<PagoDeuda> findByIdPagoDeudaForUpdate(@Param("idPagoDeuda") Long idPagoDeuda);

    Optional<PagoDeuda> findByDeudaIdDeudaAndIdempotencyKey(Long idDeuda, String idempotencyKey);
}
