package com.sonograma.repository;

import com.sonograma.entity.Deuda;
import com.sonograma.enums.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DeudaRepository extends JpaRepository<Deuda, Long> {

    List<Deuda> findByClienteIdClienteAndActivaTrueOrderByFechaCreacionDesc(Long idCliente);

    long countByClienteIdClienteAndActivaTrue(Long idCliente);

    List<Deuda> findAllByActivaTrueOrderByFechaDeudaDescFechaCreacionDesc();

    List<Deuda> findByEstadoPagoNotAndActivaTrue(EstadoPago estadoPago);

    Optional<Deuda> findByVentaIdVentaAndActivaTrue(Long idVenta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Deuda d WHERE d.idDeuda = :id")
    Optional<Deuda> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT SUM(d.montoPendiente) FROM Deuda d WHERE d.estadoPago <> 'PAGADO' AND d.activa = true")
    BigDecimal sumMontoPendiente();

    @Query("SELECT COUNT(DISTINCT d.cliente.idCliente) FROM Deuda d WHERE d.estadoPago <> 'PAGADO' AND d.activa = true")
    Long countDeudoresActivos();

    @Query("""
        SELECT d FROM Deuda d
        LEFT JOIN d.cliente c
        LEFT JOIN d.venta v
        WHERE d.activa = true
          AND (LOWER(CONCAT(COALESCE(c.nombre, ''), ' ', COALESCE(c.apellido, ''))) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(d.nombreDeudorManual, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(d.mailManual, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(d.instagramManual, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(d.ciManual, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(d.numeroFactura, COALESCE(v.numeroFactura, ''))) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY COALESCE(d.fechaDeuda, d.fechaVenta) DESC, d.fechaCreacion DESC
        """)
    List<Deuda> buscarPendientes(String q);
}
