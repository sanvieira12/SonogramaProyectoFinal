package com.sonograma.repository;

import com.sonograma.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    List<Venta> findByClienteIdCliente(Long idCliente);
    List<Venta> findByEstado(String estado);
}
