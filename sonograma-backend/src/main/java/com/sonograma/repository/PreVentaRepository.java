package com.sonograma.repository;

import com.sonograma.entity.PreVenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreVentaRepository extends JpaRepository<PreVenta, Long> {
    List<PreVenta> findAllByOrderByFechaDescIdPreVentaDesc();
}
