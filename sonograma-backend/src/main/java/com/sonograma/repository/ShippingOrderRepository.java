package com.sonograma.repository;

import com.sonograma.entity.ShippingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface ShippingOrderRepository extends JpaRepository<ShippingOrder, Long> {

    @Query("SELECT COUNT(s) FROM ShippingOrder s WHERE YEAR(s.fechaOrden) = :anio")
    long countByAnio(int anio);

    Optional<ShippingOrder> findByNumero(String numero);

    @Modifying
    @Query(value = """
        UPDATE shipping_order so
        SET estado = 'RECIBIDO'
        WHERE so.estado = 'PENDIENTE'
          AND EXISTS (
              SELECT 1
              FROM shipping_order_item soi
              JOIN disco d ON d.id_disco = soi.id_disco
              WHERE soi.id_shipping_order = so.id_shipping_order
                AND d.procedencia = 'IMPORTADO'
          )
        """, nativeQuery = true)
    int marcarImportadasComoRecibidas();
}
