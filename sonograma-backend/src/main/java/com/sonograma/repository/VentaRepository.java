package com.sonograma.repository;

import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    List<Venta> findAllByOrderByFechaVentaDesc();

    List<Venta> findByClienteIdClienteOrderByFechaVentaDesc(Long idCliente);

    long countByClienteIdCliente(Long idCliente);

    List<Venta> findByEstado(EstadoVenta estado);

    /** Fetches all profit inputs for a period in one query, excluding cancelled sales. */
    @Query("""
        SELECT DISTINCT v FROM Venta v
        LEFT JOIN FETCH v.detalles dv
        LEFT JOIN FETCH dv.disco
        WHERE v.estado <> com.sonograma.enums.EstadoVenta.CANCELADA
          AND (:desde IS NULL OR v.fechaVenta >= :desde)
          AND (:hasta IS NULL OR v.fechaVenta <= :hasta)
        ORDER BY v.fechaVenta ASC, v.idVenta ASC
        """)
    List<Venta> findAllForProfitPeriod(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Query(value =
        "SELECT TO_CHAR(fecha_venta, 'YYYY-MM') AS mes, " +
        "TO_CHAR(fecha_venta, 'Mon YY') AS etiqueta, " +
        "COUNT(*) AS cantidad, " +
        "COALESCE(SUM(CASE " +
        "WHEN subtotal IS NOT NULL THEN subtotal * (100 - COALESCE(descuento_porcentaje, 0)) / 100 " +
        "WHEN precio_venta IS NOT NULL THEN precio_venta " +
        "WHEN total_final IS NOT NULL THEN GREATEST(total_final - COALESCE(costo_envio, 0), 0) " +
        "WHEN total IS NOT NULL THEN GREATEST(total - COALESCE(costo_envio, 0), 0) " +
        "ELSE 0 END), 0) AS total_monto " +
        "FROM venta " +
        "WHERE estado <> 'CANCELADA' " +
        "GROUP BY TO_CHAR(fecha_venta, 'YYYY-MM'), TO_CHAR(fecha_venta, 'Mon YY') " +
        "ORDER BY TO_CHAR(fecha_venta, 'YYYY-MM') ASC",
        nativeQuery = true)
    List<Object[]> obtenerVentasAgrupadasPorMes();

    @Query("SELECT COUNT(v) FROM Venta v WHERE YEAR(v.fechaVenta) = :anio")
    long countByAnio(@Param("anio") int anio);

    @Query("""
        SELECT v FROM Venta v
        WHERE v.estado <> com.sonograma.enums.EstadoVenta.CANCELADA
          AND (:desde IS NULL OR v.fechaVenta >= :desde)
          AND (:hasta IS NULL OR v.fechaVenta <= :hasta)
          AND (:canal IS NULL OR v.canalVenta = :canal)
          AND (:q IS NULL OR :q = ''
               OR LOWER(v.clienteNombreSnapshot) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(v.disco.artista, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(v.disco.album, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR EXISTS (
                    SELECT 1 FROM DetalleVenta dv
                    WHERE dv.venta = v
                      AND (
                          LOWER(COALESCE(dv.artistaSnap, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(COALESCE(dv.albumSnap, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(COALESCE(dv.descripcionSnap, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(COALESCE(dv.codigoSnap, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                      )
               )
               OR LOWER(COALESCE(v.numeroFactura, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY v.fechaVenta DESC
        """)
    List<Venta> buscarLibro(
        @Param("desde") LocalDateTime desde,
        @Param("hasta") LocalDateTime hasta,
        @Param("canal") com.sonograma.enums.CanalVenta canal,
        @Param("q") String q
    );
}
