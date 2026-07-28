package com.sonograma.repository;

import com.sonograma.entity.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
    List<DetalleVenta> findByVentaIdVenta(Long idVenta);

    @Query("SELECT d FROM DetalleVenta d WHERE d.copyIdsSnapshot IS NOT NULL AND d.copyIdsSnapshot <> ''")
    List<DetalleVenta> findAllWithCopyIds();
}
