package com.sonograma.repository;

import com.sonograma.entity.PagoDeuda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface PagoDeudaRepository extends JpaRepository<PagoDeuda, Long> {
    @Query("""
        SELECT p FROM PagoDeuda p
        JOIN FETCH p.deuda d
        LEFT JOIN FETCH d.venta v
        LEFT JOIN FETCH d.cliente c
        WHERE COALESCE(p.anulado, false) = false
          AND COALESCE(d.activa, true) = true
          AND (v IS NULL OR v.estado <> com.sonograma.enums.EstadoVenta.CANCELADA)
          AND p.fechaPago BETWEEN :desde AND :hasta
        ORDER BY p.fechaPago ASC, p.idPagoDeuda ASC
        """)
    List<PagoDeuda> findValidosEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

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
