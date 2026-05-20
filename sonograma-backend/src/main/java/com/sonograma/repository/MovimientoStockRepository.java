package com.sonograma.repository;

import com.sonograma.entity.MovimientoStock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {
    List<MovimientoStock> findByDiscoIdDiscoOrderByFechaMovimientoDesc(Long idDisco);
    List<MovimientoStock> findByTipoMovimiento(String tipoMovimiento);
}
