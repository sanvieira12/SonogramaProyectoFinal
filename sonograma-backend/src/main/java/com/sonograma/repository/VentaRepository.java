package com.sonograma.repository;

import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    List<Venta> findAllByOrderByFechaVentaDesc();

    List<Venta> findByClienteIdClienteOrderByFechaVentaDesc(Long idCliente);

    List<Venta> findByEstado(EstadoVenta estado);

    @Query(value =
        "SELECT TO_CHAR(fecha_venta, 'YYYY-MM') AS mes, " +
        "TO_CHAR(fecha_venta, 'Mon YY') AS etiqueta, " +
        "COUNT(*) AS cantidad, " +
        "COALESCE(SUM(total), 0) AS total_monto " +
        "FROM venta " +
        "WHERE estado <> 'CANCELADA' " +
        "GROUP BY TO_CHAR(fecha_venta, 'YYYY-MM'), TO_CHAR(fecha_venta, 'Mon YY') " +
        "ORDER BY TO_CHAR(fecha_venta, 'YYYY-MM') ASC",
        nativeQuery = true)
    List<Object[]> obtenerVentasAgrupadasPorMes();
}
