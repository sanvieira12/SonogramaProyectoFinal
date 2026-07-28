package com.sonograma.repository;

import com.sonograma.entity.DetalleVenta;
import com.sonograma.enums.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
    List<DetalleVenta> findByVentaIdVenta(Long idVenta);

    @Query("""
        SELECT d FROM DetalleVenta d
        JOIN FETCH d.venta v
        WHERE d.copyIdsSnapshot IS NOT NULL
          AND d.copyIdsSnapshot <> ''
          AND v.estado <> :cancelled
        """)
    List<DetalleVenta> findAllWithCopyIdsFromActiveSales(@Param("cancelled") EstadoVenta cancelled);
}
