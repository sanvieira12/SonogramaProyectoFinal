package com.sonograma.repository;

import com.sonograma.entity.GastoTienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GastoTiendaRepository extends JpaRepository<GastoTienda, Long> {
    List<GastoTienda> findAllByOrderByFechaDescIdGastoDesc();
    List<GastoTienda> findByFechaBetween(LocalDate desde, LocalDate hasta);

    List<GastoTienda> findByFechaBetweenOrderByFechaAscIdGastoAsc(LocalDate desde, LocalDate hasta);
}
