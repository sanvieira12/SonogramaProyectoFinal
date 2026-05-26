package com.sonograma.repository;

import com.sonograma.entity.Deuda;
import com.sonograma.enums.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DeudaRepository extends JpaRepository<Deuda, Long> {

    List<Deuda> findByClienteIdClienteOrderByFechaCreacionDesc(Long idCliente);

    List<Deuda> findByEstadoPagoNot(EstadoPago estadoPago);

    Optional<Deuda> findByVentaIdVenta(Long idVenta);

    @Query("SELECT SUM(d.montoPendiente) FROM Deuda d WHERE d.estadoPago <> 'PAGADO'")
    BigDecimal sumMontoPendiente();

    @Query("SELECT COUNT(DISTINCT d.cliente.idCliente) FROM Deuda d WHERE d.estadoPago <> 'PAGADO'")
    Long countDeudoresActivos();

    @Query("""
        SELECT d FROM Deuda d
        WHERE d.estadoPago <> com.sonograma.enums.EstadoPago.PAGADO
          AND (LOWER(CONCAT(d.cliente.nombre, ' ', COALESCE(d.cliente.apellido, ''))) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(d.venta.numeroFactura, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY d.fechaCreacion DESC
        """)
    List<Deuda> buscarPendientes(String q);
}
