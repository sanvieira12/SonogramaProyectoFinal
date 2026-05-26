package com.sonograma.repository;

import com.sonograma.entity.ShippingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ShippingOrderRepository extends JpaRepository<ShippingOrder, Long> {

    @Query("SELECT COUNT(s) FROM ShippingOrder s WHERE YEAR(s.fechaOrden) = :anio")
    long countByAnio(int anio);

    Optional<ShippingOrder> findByNumero(String numero);
}
